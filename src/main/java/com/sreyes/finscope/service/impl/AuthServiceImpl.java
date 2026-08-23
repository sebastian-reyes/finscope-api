package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.AuthResponse;
import com.sreyes.finscope.api.model.LoginRequest;
import com.sreyes.finscope.api.model.RegisterRequest;
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
import com.sreyes.finscope.service.AuthService;
import com.sreyes.finscope.util.constants.Constants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link AuthService}.
 * Emite un token de acceso de vida corta y un token de refresco de un solo uso, del que
 * solo se guarda su hash. Al renovar, el token consumido se revoca y se emite uno nuevo,
 * de modo que un token filtrado deja de servir en cuanto el usuario legítimo renueva.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private static final String TOKEN_TYPE = "Bearer";
  private static final int REFRESH_TOKEN_BYTES = 32;

  private final UserRepository userRepository;
  private final UserIdentityRepository userIdentityRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtService jwtService;
  private final JwtProperties jwtProperties;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

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
    return userRepository.findByEmailIgnoreCase(request.getEmail().trim())
        .filter(user -> user.isActive() && user.getPasswordHash() != null
            && passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
        .switchIfEmpty(Mono.error(
            new InvalidCredentialsException(Constants.INVALID_CREDENTIALS)))
        .flatMap(this::issueCredentials);
  }

  @Override
  public Mono<AuthResponse> refresh(String refreshToken) {
    return refreshTokenRepository.findByTokenHash(hash(refreshToken))
        .filter(token -> !token.isRevoked()
            && token.getExpiresAt().isAfter(LocalDateTime.now(clock)))
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

  /**
   * Da de alta un usuario nuevo junto con su identidad local.
   *
   * @param email   correo ya normalizado
   * @param request datos de alta del usuario
   * @return el usuario recién creado
   */
  private Mono<User> createUser(String email, RegisterRequest request) {
    User user = new User();
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setDisplayName(request.getDisplayName());
    user.setActive(true);
    user.setCreatedAt(LocalDateTime.now(clock));
    return userRepository.save(user)
        .flatMap(saved -> saveLocalIdentity(saved).thenReturn(saved));
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
      return Mono.error(new EmailAlreadyRegisteredException(
          Constants.EMAIL_ALREADY_REGISTERED.replace("{}", user.getEmail())));
    }
    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    if (request.getDisplayName() != null) {
      user.setDisplayName(request.getDisplayName());
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
   * Convierte el usuario en su representación pública.
   *
   * @param user usuario autenticado
   * @return la representación del usuario
   */
  private UserResponse toUserResponse(User user) {
    UserResponse response = new UserResponse(user.getId(), user.getEmail());
    response.setDisplayName(user.getDisplayName());
    return response;
  }
}
