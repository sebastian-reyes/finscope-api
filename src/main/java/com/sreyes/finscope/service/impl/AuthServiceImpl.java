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
import com.sreyes.finscope.service.AuthService;
import com.sreyes.finscope.service.CategoryService;
import com.sreyes.finscope.util.constants.Constants;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
  private static final int REFRESH_TOKEN_BYTES = 32;

  /**
   * Longitud del valor con el que se genera el hash de comparación de descarte.
   */
  private static final int DUMMY_SECRET_BYTES = 32;

  private final UserRepository userRepository;
  private final UserIdentityRepository userIdentityRepository;
  private final CategoryService categoryService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;
  private final Clock clock;
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

  @Override
  public Mono<AuthResponse> register(RegisterRequest request) {
    String email = request.getEmail().trim();
    return userRepository.findByEmailIgnoreCase(email)
        .flatMap(existing -> claimSeededAccount(existing, request))
        .switchIfEmpty(Mono.defer(() -> createUser(email, request)))
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

  @Override
  public Mono<AuthResponse> refresh(String refreshToken) {
    return refreshTokenRepository.findByTokenHash(hash(refreshToken))
        .flatMap(this::requireUsableRefreshToken)
        .switchIfEmpty(Mono.error(
            new InvalidRefreshTokenException(Constants.INVALID_REFRESH_TOKEN)))
        .flatMap(this::revoke)
        .flatMap(token -> userRepository.findById(token.getUserId()))
        .filter(User::isActive)
        .switchIfEmpty(Mono.error(
            new InvalidRefreshTokenException(Constants.INVALID_REFRESH_TOKEN)))
        .flatMap(this::issueCredentials);
  }

  @Override
  public Mono<Void> logout(String refreshToken) {
    return refreshTokenRepository.findByTokenHash(hash(refreshToken))
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
   * Comprueba que un token de refresco sigue sirviendo.
   * Presentar uno ya consumido no es un descuido: cada renovación entrega uno nuevo, así
   * que verlo por segunda vez indica que hay una copia en circulación. Como no puede
   * saberse cuál de las dos partes es la legítima, se revocan todos los del usuario y ambas
   * tienen que volver a identificarse.
   *
   * @param token token localizado por su hash
   * @return el token si es utilizable, o un error si no lo es
   */
  private Mono<RefreshToken> requireUsableRefreshToken(RefreshToken token) {
    if (token.isRevoked()) {
      log.warn("Refresh token reuse detected for user {}; revoking active tokens",
          token.getUserId());
      return refreshTokenRepository.revokeAllByUserId(token.getUserId())
          .then(Mono.error(
              new InvalidRefreshTokenException(Constants.INVALID_REFRESH_TOKEN)));
    }
    if (!token.getExpiresAt().isAfter(LocalDateTime.now(clock))) {
      return Mono.empty();
    }
    return Mono.just(token);
  }

  /**
   * Da de alta un usuario nuevo junto con su identidad local.
   *
   * @param email   correo ya normalizado
   * @param request datos de alta del usuario
   * @return el usuario recién creado
   */
  private Mono<User> createUser(String email, RegisterRequest request) {
    return encodePassword(request.getPassword())
        .map(passwordHash -> {
          User user = new User();
          user.setEmail(email);
          user.setPasswordHash(passwordHash);
          user.setDisplayName(normaliseDisplayName(request.getDisplayName()));
          user.setActive(true);
          user.setCreatedAt(LocalDateTime.now(clock));
          return user;
        })
        .flatMap(userRepository::save)
        .flatMap(saved -> saveLocalIdentity(saved).thenReturn(saved))
        // La categoría es obligatoria en cada movimiento, así que una cuenta sin catálogo
        // no podría registrar ninguno: se siembra aquí, antes de devolver las credenciales.
        .flatMap(saved -> categoryService.seedDefaults(saved.getId()).thenReturn(saved));
  }

  /**
   * Fija la contraseña de una cuenta que aún no tiene credenciales locales.
   * Es el caso de la cuenta sembrada al adoptar los datos anteriores al modelo
   * multiusuario: quien se registra con ese correo la reclama en lugar de recibir un
   * conflicto. Si la cuenta ya tiene contraseña, el correo está ocupado.
   *
   * @param user    cuenta existente para ese correo
   * @param request datos de alta del usuario
   * @return la cuenta con sus credenciales ya establecidas
   */
  private Mono<User> claimSeededAccount(User user, RegisterRequest request) {
    if (user.getPasswordHash() != null) {
      return Mono.error(
          new EmailAlreadyRegisteredException(Constants.EMAIL_ALREADY_REGISTERED));
    }
    return encodePassword(request.getPassword())
        .map(passwordHash -> {
          user.setPasswordHash(passwordHash);
          if (request.getDisplayName() != null) {
            user.setDisplayName(normaliseDisplayName(request.getDisplayName()));
          }
          return user;
        })
        .flatMap(userRepository::save);
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
   * Emite el par de tokens del usuario y compone la respuesta de autenticación.
   *
   * @param user usuario autenticado
   * @return las credenciales emitidas
   */
  private Mono<AuthResponse> issueCredentials(User user) {
    String refreshToken = generateRefreshToken();
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
    token.setTokenHash(hash(refreshToken));
    token.setExpiresAt(LocalDateTime.now(clock).plus(jwtProperties.refreshTokenTtl()));
    token.setRevoked(false);
    token.setCreatedAt(LocalDateTime.now(clock));
    return refreshTokenRepository.save(token).then();
  }

  /**
   * Marca un token de refresco como revocado.
   *
   * @param token token a revocar
   * @return el token ya revocado
   */
  private Mono<RefreshToken> revoke(RefreshToken token) {
    token.setRevoked(true);
    return refreshTokenRepository.save(token);
  }

  /**
   * Genera un token de refresco aleatorio con entropía criptográfica.
   *
   * @return el valor del token
   */
  private String generateRefreshToken() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Calcula el hash SHA-256 con el que se almacena un token de refresco.
   *
   * @param token valor del token
   * @return el hash en hexadecimal
   */
  private String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required to store refresh tokens", ex);
    }
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
    UserResponse response = new UserResponse(user.getId(), user.getEmail());
    response.setDisplayName(user.getDisplayName());
    return response;
  }
}
