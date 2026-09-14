package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.ChangeEmailRequest;
import com.sreyes.finscope.api.model.ResetPasswordRequest;
import com.sreyes.finscope.exception.custom.EmailAlreadyRegisteredException;
import com.sreyes.finscope.exception.custom.InvalidAccountTokenException;
import com.sreyes.finscope.exception.custom.InvalidCredentialsException;
import com.sreyes.finscope.model.entity.AccountToken;
import com.sreyes.finscope.model.entity.User;
import com.sreyes.finscope.model.entity.UserIdentity;
import com.sreyes.finscope.repository.AccountTokenRepository;
import com.sreyes.finscope.repository.RefreshTokenRepository;
import com.sreyes.finscope.repository.UserIdentityRepository;
import com.sreyes.finscope.repository.UserRepository;
import com.sreyes.finscope.security.LoginAttemptService;
import com.sreyes.finscope.security.MailProperties;
import com.sreyes.finscope.security.SecureTokens;
import com.sreyes.finscope.service.AccountService;
import com.sreyes.finscope.service.MailService;
import com.sreyes.finscope.util.constants.Constants;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Implementación del servicio {@link AccountService}.
 *
 * <p>Las tres operaciones giran sobre el mismo objeto: un enlace de un solo uso que se manda
 * al buzón y caduca. Emitirlo descarta el anterior del mismo propósito —si no, cada petición
 * sumaría una llave más a la misma puerta— y consumirlo lo marca antes de hacer nada, de modo
 * que un fallo a mitad quema el enlace en lugar de dejarlo utilizable otra vez.</p>
 *
 * <p>Ninguna de ellas espera al correo. La respuesta dice que el mensaje va en camino, no que
 * haya llegado: la entrega depende de un tercero que puede tardar segundos, y colgar de él la
 * petición del usuario haría que un servidor de correo lento pareciera un fallo de FinScope.
 * Si el envío falla, queda en el registro.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

  private final UserRepository userRepository;
  private final UserIdentityRepository userIdentityRepository;
  private final AccountTokenRepository accountTokenRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final MailService mailService;
  private final MailProperties mailProperties;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;
  private final Clock clock;

  @Override
  public Mono<Void> sendEmailVerification(Long userId) {
    return userRepository.findById(userId)
        // Sobre una cuenta ya verificada no hay nada que mandar. No es un error: reenviar es
        // un botón que el usuario puede pulsar dos veces, y la segunda no debería quejarse.
        .filter(user -> !user.isEmailVerified())
        .flatMap(user -> issue(user.getId(), AccountToken.EMAIL_VERIFICATION, null,
            mailProperties.verificationTtl())
            .flatMap(token -> dispatch(mailService.sendEmailVerification(
                user.getEmail(), user.getDisplayName(), token))))
        .then();
  }

  @Override
  public Mono<Void> confirmEmailVerification(String token) {
    return consume(token, AccountToken.EMAIL_VERIFICATION)
        .flatMap(consumed -> userRepository.findById(consumed.getUserId()))
        .flatMap(user -> {
          user.setEmailVerified(true);
          return userRepository.save(user);
        })
        .doOnNext(user -> log.info("Email verified for user {}", user.getId()))
        .then();
  }

  @Override
  public Mono<Void> requestEmailChange(Long userId, ChangeEmailRequest request) {
    return Mono.defer(() -> {
      String email = request.getEmail().trim();
      return userRepository.findById(userId)
          .switchIfEmpty(Mono.error(
              new InvalidCredentialsException(Constants.INVALID_CREDENTIALS)))
          .flatMap(user -> verifyPassword(user, request.getPassword()))
          .flatMap(user -> requireAvailable(email, user))
          .flatMap(user -> issue(user.getId(), AccountToken.EMAIL_CHANGE, email,
              mailProperties.emailChangeTtl())
              .flatMap(token -> dispatch(mailService.sendEmailChange(
                  email, user.getDisplayName(), token))
                  // El aviso a la dirección de siempre se manda aparte y no encadenado al
                  // anterior: es el único modo en que el dueño legítimo se entera de un
                  // cambio que no ha pedido, así que no puede depender de que el otro salga.
                  .then(dispatch(mailService.sendEmailChangeNotice(
                      user.getEmail(), user.getDisplayName(), email)))))
          .then();
    });
  }

  @Override
  public Mono<Void> confirmEmailChange(String token) {
    return consume(token, AccountToken.EMAIL_CHANGE)
        .flatMap(consumed -> userRepository.findById(consumed.getUserId())
            .flatMap(user -> applyEmailChange(user, consumed.getTargetEmail())))
        .then();
  }

  @Override
  public Mono<Void> requestPasswordReset(String email) {
    return Mono.defer(() -> userRepository.findByEmailIgnoreCase(email.trim())
        // Una cuenta sin contraseña no tiene ninguna que recuperar: es la sembrada al
        // adoptar los datos anteriores al modelo multiusuario, y se reclama registrándose
        // con ese correo. Restablecer aquí le pondría credenciales por otra puerta.
        .filter(user -> user.isActive() && user.getPasswordHash() != null)
        .flatMap(user -> issue(user.getId(), AccountToken.PASSWORD_RESET, null,
            mailProperties.passwordResetTtl())
            .flatMap(token -> dispatch(mailService.sendPasswordReset(
                    user.getEmail(), user.getDisplayName(), token))
                // El envío completa vacío, así que sin volver a emitir al usuario la cadena
                // entera se quedaría sin valor y el aviso de abajo saldría también cuando la
                // cuenta existe, que es justo al revés de lo que dice.
                .thenReturn(user)))
        .doOnNext(user -> log.info("Password reset link sent for user {}", user.getId()))
        .switchIfEmpty(Mono.fromRunnable(
            () -> log.info("Password reset requested for an address with no usable account")))
        .then());
  }

  @Override
  public Mono<Void> resetPassword(ResetPasswordRequest request) {
    return consume(request.getToken(), AccountToken.PASSWORD_RESET)
        .flatMap(consumed -> userRepository.findById(consumed.getUserId()))
        .flatMap(user -> encodePassword(request.getPassword())
            .flatMap(passwordHash -> {
              user.setPasswordHash(passwordHash);
              // Quien recibe el enlace demuestra con ello que tiene el buzón, que es
              // exactamente lo que verifica el correo. No verificarlo aquí obligaría a pedir
              // otro correo para comprobar algo que ya se acaba de comprobar.
              user.setEmailVerified(true);
              return userRepository.save(user);
            }))
        // Se restablece la contraseña cuando se sospecha que otro la tiene. Dejar vivos los
        // tokens de refresco ya emitidos dejaría dentro a ese otro durante un mes.
        .flatMap(user -> refreshTokenRepository.revokeAllByUserId(user.getId())
            .thenReturn(user))
        // El bloqueo por intentos fallidos se olvida: quien acaba de demostrar que es el
        // dueño no debería quedarse fuera por los fallos que provocaron el restablecimiento.
        .doOnNext(user -> {
          loginAttemptService.recordSuccess(user.getEmail());
          log.info("Password reset completed for user {}", user.getId());
        })
        .then();
  }

  /**
   * Comprueba que la dirección propuesta puede usarse antes de mandar nada a ella.
   *
   * @param email dirección propuesta, ya recortada
   * @param user  usuario que pide el cambio
   * @return el usuario si la dirección está libre, o un error de conflicto si no lo está
   */
  private Mono<User> requireAvailable(String email, User user) {
    if (email.equalsIgnoreCase(user.getEmail())) {
      return Mono.error(new EmailAlreadyRegisteredException(Constants.EMAIL_UNCHANGED));
    }
    return userRepository.findByEmailIgnoreCase(email)
        .flatMap(other -> Mono.<User>error(
            new EmailAlreadyRegisteredException(Constants.EMAIL_ALREADY_REGISTERED)))
        .switchIfEmpty(Mono.just(user));
  }

  /**
   * Pone la dirección nueva como correo de la cuenta.
   * Se vuelve a comprobar que sigue libre: entre pedir el cambio y confirmarlo puede haber
   * pasado una hora, y en ese rato otra persona ha podido registrarse con ella.
   *
   * @param user     usuario dueño del cambio
   * @param newEmail dirección a la que se mueve la cuenta
   * @return el usuario ya guardado
   */
  private Mono<User> applyEmailChange(User user, String newEmail) {
    return userRepository.findByEmailIgnoreCase(newEmail)
        .filter(other -> !other.getId().equals(user.getId()))
        .flatMap(other -> Mono.<User>error(
            new EmailAlreadyRegisteredException(Constants.EMAIL_ALREADY_REGISTERED)))
        .switchIfEmpty(Mono.defer(() -> {
          String previous = user.getEmail();
          user.setEmail(newEmail);
          // Acaba de demostrarse que la cuenta recibe correo en la dirección nueva, que es
          // justo lo que comprueba la verificación.
          user.setEmailVerified(true);
          return userRepository.save(user)
              .flatMap(saved -> moveLocalIdentity(previous, newEmail).thenReturn(saved))
              .doOnNext(saved -> log.info("Email changed for user {}", saved.getId()));
        }));
  }

  /**
   * Lleva la identidad local del usuario a su correo nuevo.
   * Para el proveedor local el sujeto de la identidad es el correo, así que dejarlo en el
   * viejo haría que la tabla dijera una cosa y la cuenta otra. Una cuenta sin identidad local
   * —la sembrada al adoptar los datos previos— no tiene nada que mover.
   *
   * @param previous correo anterior de la cuenta
   * @param newEmail correo nuevo de la cuenta
   * @return Mono vacío al completar el traslado
   */
  private Mono<Void> moveLocalIdentity(String previous, String newEmail) {
    return userIdentityRepository
        .findByProviderAndSubject(UserIdentity.LOCAL_PROVIDER, previous)
        .flatMap(identity -> {
          identity.setSubject(newEmail);
          return userIdentityRepository.save(identity);
        })
        .then();
  }

  /**
   * Emite un enlace de un solo uso y devuelve el valor que viaja al usuario.
   * Los anteriores del mismo propósito se descartan: pedir otro enlace tiene que dejar sin
   * valor al de antes.
   *
   * @param userId      identificador del usuario
   * @param purpose     qué hará el enlace al consumirse
   * @param targetEmail dirección de destino, solo en el cambio de correo
   * @param ttl         cuánto vale el enlace
   * @return el valor del enlace, que no vuelve a estar disponible después
   */
  private Mono<String> issue(Long userId, String purpose, String targetEmail, Duration ttl) {
    String value = SecureTokens.generate();
    LocalDateTime now = LocalDateTime.now(clock);
    AccountToken token = new AccountToken();
    token.setUserId(userId);
    token.setPurpose(purpose);
    token.setTokenHash(SecureTokens.hash(value));
    token.setTargetEmail(targetEmail);
    token.setExpiresAt(now.plus(ttl));
    token.setCreatedAt(now);
    return accountTokenRepository.deleteByUserIdAndPurpose(userId, purpose)
        .then(accountTokenRepository.save(token))
        .thenReturn(value);
  }

  /**
   * Gasta el enlace recibido y devuelve lo que decía.
   * El propósito se comprueba aquí y no en la consulta para que un enlace de otro tipo no
   * pueda usarse en la operación equivocada: el de verificar el correo no debe servir para
   * cambiar la contraseña, aunque los dos salgan del mismo buzón.
   *
   * @param token   valor recibido del cliente
   * @param purpose propósito que debe tener
   * @return el enlace ya marcado como consumido, o un error si no sirve
   */
  private Mono<AccountToken> consume(String token, String purpose) {
    LocalDateTime now = LocalDateTime.now(clock);
    return accountTokenRepository.findByTokenHash(SecureTokens.hash(token))
        .filter(found -> purpose.equals(found.getPurpose())
            && found.getConsumedAt() == null
            && found.getExpiresAt().isAfter(now))
        .switchIfEmpty(Mono.error(
            new InvalidAccountTokenException(Constants.INVALID_ACCOUNT_TOKEN)))
        // Se marca antes de hacer nada: si algo falla a mitad, el enlace queda quemado en
        // lugar de seguir sirviendo. Es lo prudente cuando abre la puerta de una cuenta.
        .flatMap(found -> {
          found.setConsumedAt(now);
          return accountTokenRepository.save(found);
        });
  }

  /**
   * Comprueba la contraseña en curso fuera del bucle de eventos.
   * BCrypt es lento a propósito, así que compararlo aquí bloquearía el hilo que atiende al
   * resto de peticiones.
   *
   * @param user     usuario dueño de la cuenta
   * @param password contraseña recibida en la petición
   * @return el usuario, o un error de credenciales inválidas
   */
  private Mono<User> verifyPassword(User user, String password) {
    return Mono.fromCallable(() -> {
      if (user.getPasswordHash() == null
          || !passwordEncoder.matches(password, user.getPasswordHash())) {
        throw new InvalidCredentialsException(Constants.INVALID_CREDENTIALS);
      }
      return user;
    }).subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * Calcula el hash de una contraseña fuera del bucle de eventos.
   *
   * @param password contraseña en claro
   * @return el hash con el que se almacena
   */
  private Mono<String> encodePassword(String password) {
    return Mono.fromCallable(() -> passwordEncoder.encode(password))
        .subscribeOn(Schedulers.boundedElastic());
  }

  /**
   * Pone el correo en camino sin esperar a que llegue.
   * Lo que la API responde es que el mensaje va a salir, no que haya salido: si se esperara,
   * un servidor de correo lento o caído convertiría una operación correcta en un error en
   * pantalla, y el usuario no podría hacer nada distinto al respecto.
   *
   * @param delivery envío a lanzar
   * @return Mono vacío, que completa de inmediato
   */
  private Mono<Void> dispatch(Mono<Void> delivery) {
    return Mono.fromRunnable(() -> delivery
        .doOnError(ex -> log.error("Could not deliver the account email", ex))
        .onErrorComplete()
        .subscribe());
  }
}
