package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.AuthResponse;
import com.sreyes.finscope.api.model.LoginRequest;
import com.sreyes.finscope.api.model.RegisterRequest;
import com.sreyes.finscope.api.model.UpdateUserRequest;
import com.sreyes.finscope.api.model.UserResponse;
import com.sreyes.finscope.exception.custom.EmailAlreadyRegisteredException;
import com.sreyes.finscope.exception.custom.InvalidCredentialsException;
import com.sreyes.finscope.exception.custom.InvalidRefreshTokenException;
import com.sreyes.finscope.model.entity.RefreshToken;
import com.sreyes.finscope.model.entity.User;
import com.sreyes.finscope.model.entity.UserIdentity;
import com.sreyes.finscope.repository.RefreshTokenRepository;
import com.sreyes.finscope.repository.UserIdentityRepository;
import com.sreyes.finscope.repository.UserRepository;
import com.sreyes.finscope.security.JwtProperties;
import com.sreyes.finscope.security.JwtService;
import com.sreyes.finscope.security.LoginAttemptService;
import com.sreyes.finscope.security.SecureTokens;
import com.sreyes.finscope.service.AccountService;
import com.sreyes.finscope.service.AuthService;
import com.sreyes.finscope.service.CategoryService;
import com.sreyes.finscope.util.constants.Constants;
import jakarta.annotation.PostConstruct;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Implementación del servicio {@link AuthService}.
 * Emite un token de acceso de vida corta y un token de refresco de un solo uso, del que
 * solo se guarda su hash. Al renovar, el token consumido se revoca y se emite uno nuevo,
 * de modo que un token filtrado deja de servir en cuanto el usuario legítimo renueva.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private static final String TOKEN_TYPE = "Bearer";

  /**
   * Longitud del valor con el que se genera el hash de comparación de descarte.
   */
  private static final int DUMMY_SECRET_BYTES = 32;

  private final UserRepository userRepository;
  private final UserIdentityRepository userIdentityRepository;
  private final CategoryService categoryService;
  private final AccountService accountService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;
  private final Clock clock;
  private final TransactionalOperator transactionalOperator;
  private final SecureRandom secureRandom = new SecureRandom();

  /**
   * Hash de una contraseña aleatoria que nadie conoce, contra el que se compara cuando el
   * correo no corresponde a ninguna cuenta utilizable.
   * Sin él, un correo desconocido se resolvería sin llegar a calcular ningún hash y
   * respondería mucho antes que uno registrado, lo que permitiría distinguirlos midiendo el
   * tiempo por mucho que el mensaje de error sea el mismo.
   */
  private String dummyPasswordHash;

  /**
   * Calcula el hash de descarte una sola vez, al levantar el servicio.
   * Se hace aquí y no al declarar el campo porque necesita el codificador ya inyectado, y
   * no en cada intento porque el coste de un hash es justo lo que se quiere evitar pagar de
   * más en las peticiones.
   */
  @PostConstruct
  void initDummyPasswordHash() {
    byte[] filler = new byte[DUMMY_SECRET_BYTES];
    secureRandom.nextBytes(filler);
    dummyPasswordHash = passwordEncoder.encode(
        Base64.getUrlEncoder().withoutPadding().encodeToString(filler));
  }

  /**
   * {@inheritDoc}
   *
   * <p>El alta es atómica: usuario, identidad local y catálogo de categorías se escriben en
   * una sola transacción. Antes, un fallo a mitad dejaba una cuenta con contraseña pero sin
   * categorías, que no podía registrar ningún movimiento, y al reintentar el alta respondía
   * que el correo ya estaba ocupado.</p>
   *
   * <p>Quedan fuera dos cosas, a propósito. El hash de la contraseña, que es lento a
   * propósito y retendría una conexión del pool mientras se calcula. Y el correo de
   * verificación: no condiciona el alta, y dentro de la transacción un fallo al guardar su
   * enlace abortaría en Postgres todo lo demás aunque aquí se ignore el error.</p>
   */
  @Override
  public Mono<AuthResponse> register(RegisterRequest request) {
    String email = request.getEmail().trim();
    return encodePassword(request.getPassword())
        .flatMap(passwordHash -> transactionalOperator.transactional(
            userRepository.findByEmailIgnoreCase(email)
                .flatMap(existing -> claimSeededAccount(existing, request, passwordHash))
                .switchIfEmpty(Mono.defer(() -> createUser(email, request, passwordHash)))))
        .flatMap(this::sendEmailVerification)
        .flatMap(this::issueCredentials);
  }

  @Override
  public Mono<AuthResponse> login(LoginRequest request) {
    String email = request.getEmail().trim();
    return loginAttemptService.requireNotLocked(email)
        .then(Mono.defer(() -> userRepository.findByEmailIgnoreCase(email)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())))
        .flatMap(found -> verifyPassword(found, request.getPassword()))
        .doOnNext(user -> {
          loginAttemptService.recordSuccess(email);
          log.info("Login succeeded for user {}", user.getId());
        })
        .flatMap(this::issueCredentials)
        .onErrorResume(InvalidCredentialsException.class, ex -> {
          loginAttemptService.recordFailure(email);
          log.warn("Login failed: invalid credentials");
          return Mono.error(ex);
        });
  }

  /**
   * {@inheritDoc}
   *
   * <p>El token se canjea con {@link RefreshTokenRepository#rotate}, que es atómico: de dos
   * peticiones con el mismo token solo una se lo queda. Antes se leía, se comprobaba y se
   * guardaba en pasos sueltos, y las dos podían pasar la comprobación. La que gana revoca el
   * token y guarda el nuevo en la misma transacción, así que un fallo al guardar el nuevo no
   * deja la sesión cerrada con el viejo ya gastado.</p>
   *
   * <p>La que pierde no es por fuerza un robo. Si el token se rotó hace menos de
   * {@code refresh-reuse-grace}, es la otra mitad de una carrera —dos pestañas, o la
   * aplicación instalada y el navegador— y recibe también un par nuevo. Antes esa segunda
   * petición se tomaba por una copia robada y cerraba la sesión en todos los dispositivos.
   * Pasado el margen, o si el token se revocó por otra vía, sí es una copia en circulación y
   * se revocan todos los de la cuenta. El margen es la ventana que se le concede a quien
   * tuviera una copia: por eso es corto y tiene tope.</p>
   */
  @Override
  public Mono<AuthResponse> refresh(String refreshToken) {
    String tokenHash = SecureTokens.hash(refreshToken);
    LocalDateTime now = LocalDateTime.now(clock);
    return transactionalOperator.transactional(
            refreshTokenRepository.rotate(tokenHash, now)
                .filter(rotated -> rotated > 0)
                .flatMap(rotated -> refreshTokenRepository.findByTokenHash(tokenHash))
                .flatMap(this::issueCredentialsFor))
        .switchIfEmpty(Mono.defer(() -> refreshTokenRepository.findByTokenHash(tokenHash)
            .flatMap(token -> resolveUnrotated(token, now))))
        .switchIfEmpty(Mono.error(
            new InvalidRefreshTokenException(Constants.INVALID_REFRESH_TOKEN)));
  }

  @Override
  public Mono<Void> logout(String refreshToken) {
    return refreshTokenRepository.findByTokenHash(SecureTokens.hash(refreshToken))
        .flatMap(this::revoke)
        .then();
  }

  @Override
  public Mono<UserResponse> getUser(Long userId) {
    return userRepository.findById(userId)
        .switchIfEmpty(Mono.error(
            new InvalidCredentialsException(Constants.INVALID_CREDENTIALS)))
        .map(this::toUserResponse);
  }

  @Override
  public Mono<UserResponse> updateUser(Long userId, UpdateUserRequest request) {
    return userRepository.findById(userId)
        .switchIfEmpty(Mono.error(
            new InvalidCredentialsException(Constants.INVALID_CREDENTIALS)))
        .flatMap(user -> {
          // Un campo ausente no es un cambio: se queda lo que hubiera. El nombre en blanco
          // si lo es, y deja la cuenta sin nombre, que es como puede nacer.
          if (request.getDisplayName() != null) {
            user.setDisplayName(normaliseDisplayName(request.getDisplayName()));
          }
          return userRepository.save(user);
        })
        .map(this::toUserResponse);
  }

  /**
   * Comprueba la contraseña recibida gastando el mismo tiempo exista o no la cuenta.
   * La comparación se aparta del bucle de eventos porque bcrypt está pensado para ser
   * lento: dejarla ahí bloquearía el hilo que atiende al resto de peticiones y convertiría
   * un aluvión de intentos de acceso en una caída del servicio entero.
   *
   * @param found    cuenta encontrada para el correo, si la hay
   * @param password contraseña recibida en la petición
   * @return la cuenta autenticada, o un error de credenciales inválidas
   */
  private Mono<User> verifyPassword(Optional<User> found, String password) {
    return Mono.fromCallable(() -> {
      User user = found.orElse(null);
      boolean usable = user != null && user.isActive() && user.getPasswordHash() != null;
      String hash = usable ? user.getPasswordHash() : dummyPasswordHash;
      if (!passwordEncoder.matches(password, hash) || !usable) {
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
   * Decide qué hacer con un token que esta petición no ha podido canjear.
   * Si no está revocado, es que caducó. Si se rotó hace nada, es una renovación concurrente
   * y se atiende. En cualquier otro caso es un token ya consumido que alguien vuelve a
   * presentar: como cada renovación entrega uno nuevo, eso indica que hay una copia en
   * circulación, y como no puede saberse cuál de las dos partes es la legítima, se revocan
   * todos los del usuario y ambas tienen que volver a identificarse.
   *
   * @param token token localizado por su hash
   * @param now   momento de la renovación
   * @return credenciales nuevas, vacío si el token caducó, o un error si se reutilizó
   */
  private Mono<AuthResponse> resolveUnrotated(RefreshToken token, LocalDateTime now) {
    if (!token.isRevoked()) {
      return Mono.empty();
    }
    if (isConcurrentRotation(token, now)) {
      log.info("Concurrent refresh tolerated for user {}", token.getUserId());
      return issueCredentialsFor(token);
    }
    log.warn("Refresh token reuse detected for user {}; revoking active tokens",
        token.getUserId());
    return refreshTokenRepository.revokeAllByUserId(token.getUserId())
        .then(Mono.error(new InvalidRefreshTokenException(Constants.INVALID_REFRESH_TOKEN)));
  }

  /**
   * Indica si un token revocado lo fue por una rotación lo bastante reciente como para
   * tomar esta petición por la otra mitad de una carrera.
   *
   * @param token token revocado
   * @param now   momento de la renovación
   * @return si cae dentro del margen de renovación concurrente
   */
  private boolean isConcurrentRotation(RefreshToken token, LocalDateTime now) {
    Duration grace = jwtProperties.refreshReuseGrace();
    return token.getRotatedAt() != null
        && !grace.isZero()
        && token.getExpiresAt().isAfter(now)
        && !now.isAfter(token.getRotatedAt().plus(grace));
  }

  /**
   * Emite credenciales nuevas para el dueño de un token, si su cuenta sigue activa.
   *
   * @param token token canjeado
   * @return las credenciales, o vacío si la cuenta ya no está activa
   */
  private Mono<AuthResponse> issueCredentialsFor(RefreshToken token) {
    return userRepository.findById(token.getUserId())
        .filter(User::isActive)
        .flatMap(this::issueCredentials);
  }

  /**
   * Da de alta un usuario nuevo junto con su identidad local.
   *
   * @param email        correo ya normalizado
   * @param request      datos de alta del usuario
   * @param passwordHash hash de la contraseña, ya calculado
   * @return el usuario recién creado
   */
  private Mono<User> createUser(String email, RegisterRequest request, String passwordHash) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordHash);
    user.setDisplayName(normaliseDisplayName(request.getDisplayName()));
    user.setActive(true);
    user.setCreatedAt(LocalDateTime.now(clock));
    return userRepository.save(user)
        .flatMap(saved -> saveLocalIdentity(saved).thenReturn(saved))
        .flatMap(saved -> categoryService.seedDefaults(saved.getId()).thenReturn(saved));
  }

  /**
   * Fija la contraseña de una cuenta que aún no tiene credenciales locales.
   * Es el caso de la cuenta sembrada al adoptar los datos anteriores al modelo
   * multiusuario: quien se registra con ese correo la reclama en lugar de recibir un
   * conflicto. Si la cuenta ya tiene contraseña, el correo está ocupado.
   *
   * @param user         cuenta existente para ese correo
   * @param request      datos de alta del usuario
   * @param passwordHash hash de la contraseña, ya calculado
   * @return la cuenta con sus credenciales ya establecidas
   */
  private Mono<User> claimSeededAccount(User user, RegisterRequest request,
                                        String passwordHash) {
    if (user.getPasswordHash() != null) {
      return Mono.error(
          new EmailAlreadyRegisteredException(Constants.EMAIL_ALREADY_REGISTERED));
    }
    user.setPasswordHash(passwordHash);
    if (request.getDisplayName() != null) {
      user.setDisplayName(normaliseDisplayName(request.getDisplayName()));
    }
    return userRepository.save(user);
  }

  /**
   * Registra la identidad local del usuario si todavía no existe.
   *
   * @param user usuario recién creado
   * @return Mono vacío al completar el registro de la identidad
   */
  private Mono<Void> saveLocalIdentity(User user) {
    UserIdentity identity = new UserIdentity();
    identity.setUserId(user.getId());
    identity.setProvider(UserIdentity.LOCAL_PROVIDER);
    identity.setSubject(user.getEmail());
    return userIdentityRepository.save(identity).then();
  }

  /**
   * Manda al usuario recién registrado el enlace con el que verificar su correo.
   * Un fallo aquí no tumba el alta: la cuenta ya existe y sus credenciales son válidas, así
   * que devolver un error haría creer que no se ha registrado. Queda en el registro, y el
   * usuario puede pedir el correo de nuevo desde la pantalla de su cuenta.
   *
   * @param user usuario recién dado de alta
   * @return el mismo usuario, se haya podido mandar el correo o no
   */
  private Mono<User> sendEmailVerification(User user) {
    return accountService.sendEmailVerification(user.getId())
        .doOnError(ex -> log.error("Could not start email verification for user {}",
            user.getId(), ex))
        .onErrorComplete()
        .thenReturn(user);
  }

  /**
   * Emite el par de tokens del usuario y compone la respuesta de autenticación.
   *
   * @param user usuario autenticado
   * @return las credenciales emitidas
   */
  private Mono<AuthResponse> issueCredentials(User user) {
    String refreshToken = SecureTokens.generate();
    return persistRefreshToken(user, refreshToken)
        .thenReturn(new AuthResponse(jwtService.issueAccessToken(user), refreshToken,
            TOKEN_TYPE, jwtService.accessTokenExpiresInSeconds(), toUserResponse(user)));
  }

  /**
   * Guarda el hash del token de refresco emitido junto con su caducidad.
   *
   * @param user         usuario propietario del token
   * @param refreshToken valor del token entregado al cliente
   * @return Mono vacío al completar el guardado
   */
  private Mono<Void> persistRefreshToken(User user, String refreshToken) {
    RefreshToken token = new RefreshToken();
    token.setUserId(user.getId());
    token.setTokenHash(SecureTokens.hash(refreshToken));
    token.setExpiresAt(LocalDateTime.now(clock).plus(jwtProperties.refreshTokenTtl()));
    token.setRevoked(false);
    token.setCreatedAt(LocalDateTime.now(clock));
    return refreshTokenRepository.save(token).then();
  }

  /**
   * Marca un token de refresco como revocado al cerrar la sesión.
   * Borra también la marca de rotación: un token que se cierra a propósito no debe volver a
   * aceptarse como renovación concurrente, aunque se hubiera rotado segundos antes.
   *
   * @param token token a revocar
   * @return el token ya revocado
   */
  private Mono<RefreshToken> revoke(RefreshToken token) {
    token.setRevoked(true);
    token.setRotatedAt(null);
    return refreshTokenRepository.save(token);
  }

  /**
   * Deja el nombre como se va a guardar: sin espacios de sobra y nulo si no queda nada.
   * Un nombre en blanco y la ausencia de nombre son el mismo estado, y guardarlos de dos
   * formas distintas obligaria a comprobar las dos en cada sitio que lo pinta.
   *
   * @param displayName nombre tal y como ha llegado
   * @return el nombre listo para guardar, o nulo si venia vacio
   */
  private String normaliseDisplayName(String displayName) {
    if (displayName == null) {
      return null;
    }
    String trimmed = displayName.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /**
   * Construye la representacion publica de un usuario.
   *
   * @param user usuario a representar
   * @return la representacion del usuario
   */
  private UserResponse toUserResponse(User user) {
    UserResponse response =
        new UserResponse(user.getId(), user.getEmail(), user.isEmailVerified());
    response.setDisplayName(user.getDisplayName());
    return response;
  }
}
