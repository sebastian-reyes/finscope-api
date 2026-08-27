package com.sreyes.finscope.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.api.model.LoginRequest;
import com.sreyes.finscope.api.model.RegisterRequest;
import com.sreyes.finscope.api.model.UpdateUserRequest;
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
import com.sreyes.finscope.service.CategoryService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link AuthServiceImpl}, centradas en el alta de usuarios, en la
 * validación de credenciales y en el ciclo de vida de los tokens de refresco, que son de un
 * solo uso.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final String EMAIL = "sebastian@example.com";
  private static final String PASSWORD = "una-contrasena-larga";

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserIdentityRepository userIdentityRepository;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private JwtService jwtService;

  @Mock
  private CategoryService categoryService;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  private AuthServiceImpl authService;

  @BeforeEach
  void setUp() {
    JwtProperties jwtProperties = new JwtProperties("clave-de-firma-de-al-menos-32-caracteres",
        "finscope-api", Duration.ofMinutes(15), Duration.ofDays(30));
    authService = new AuthServiceImpl(userRepository, userIdentityRepository, categoryService,
        refreshTokenRepository, jwtService, jwtProperties, passwordEncoder,
        Clock.systemDefaultZone());
    // Toda cuenta nace con su catálogo: sin él no podría registrar ni un movimiento.
    when(categoryService.seedDefaults(anyLong())).thenReturn(Mono.empty());
    when(jwtService.issueAccessToken(any(User.class))).thenReturn("access-token");
    when(jwtService.accessTokenExpiresInSeconds()).thenReturn(900L);
    when(refreshTokenRepository.save(any(RefreshToken.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(userIdentityRepository.save(any(UserIdentity.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
  }

  @Test
  @DisplayName("Registra un usuario nuevo y emite credenciales")
  void registersNewUser() {
    givenNoUserWithEmail();

    StepVerifier.create(authService.register(registerRequest()))
        .assertNext(auth -> {
          assertEquals("access-token", auth.getAccessToken());
          assertEquals("Bearer", auth.getTokenType());
          assertEquals(900L, auth.getExpiresIn());
          assertNotNull(auth.getRefreshToken());
          assertEquals(USER_ID, auth.getUser().getId());
        })
        .verifyComplete();

  }

  @Test
  @DisplayName("Guarda la contraseña cifrada, nunca en claro")
  void storesHashedPassword() {
    givenNoUserWithEmail();

    StepVerifier.create(authService.register(registerRequest()))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertTrue(passwordEncoder.matches(PASSWORD, captor.getValue().getPasswordHash()));
  }

  @Test
  @DisplayName("Rechaza el alta cuando el correo ya tiene contraseña")
  void rejectsAlreadyRegisteredEmail() {
    when(userRepository.findByEmailIgnoreCase(EMAIL))
        .thenReturn(Mono.just(existingUser(passwordEncoder.encode(PASSWORD))));

    StepVerifier.create(authService.register(registerRequest()))
        .expectError(EmailAlreadyRegisteredException.class)
        .verify();

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("Deja reclamar la cuenta sembrada sin contraseña fijando una nueva")
  void claimsSeededAccount() {
    User seeded = existingUser(null);
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Mono.just(seeded));
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    StepVerifier.create(authService.register(registerRequest()))
        .assertNext(auth -> assertEquals(USER_ID, auth.getUser().getId()))
        .verifyComplete();

    assertTrue(passwordEncoder.matches(PASSWORD, seeded.getPasswordHash()));
  }

  @Test
  @DisplayName("Autentica al usuario con la contraseña correcta")
  void logsInWithValidCredentials() {
    when(userRepository.findByEmailIgnoreCase(EMAIL))
        .thenReturn(Mono.just(existingUser(passwordEncoder.encode(PASSWORD))));

    StepVerifier.create(authService.login(loginRequest(PASSWORD)))
        .assertNext(auth -> assertEquals("access-token", auth.getAccessToken()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Rechaza el acceso con la contraseña incorrecta")
  void rejectsWrongPassword() {
    when(userRepository.findByEmailIgnoreCase(EMAIL))
        .thenReturn(Mono.just(existingUser(passwordEncoder.encode(PASSWORD))));

    StepVerifier.create(authService.login(loginRequest("otra-contrasena")))
        .expectError(InvalidCredentialsException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza el acceso de un correo que no existe")
  void rejectsUnknownEmail() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Mono.empty());

    StepVerifier.create(authService.login(loginRequest(PASSWORD)))
        .expectError(InvalidCredentialsException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza el acceso de una cuenta sembrada que aún no fue reclamada")
  void rejectsLoginOnSeededAccount() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Mono.just(existingUser(null)));

    StepVerifier.create(authService.login(loginRequest(PASSWORD)))
        .expectError(InvalidCredentialsException.class)
        .verify();
  }

  @Test
  @DisplayName("Renueva las credenciales y revoca el token de refresco consumido")
  void refreshesAndRevokesConsumedToken() {
    RefreshToken stored = refreshToken(false, LocalDateTime.now().plusDays(1));
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(stored));
    when(userRepository.findById(USER_ID))
        .thenReturn(Mono.just(existingUser(passwordEncoder.encode(PASSWORD))));

    StepVerifier.create(authService.refresh("un-token-de-refresco"))
        .assertNext(auth -> assertNotNull(auth.getRefreshToken()))
        .verifyComplete();

    assertTrue(stored.isRevoked());
  }

  @Test
  @DisplayName("Rechaza un token de refresco ya consumido")
  void rejectsAlreadyUsedRefreshToken() {
    when(refreshTokenRepository.findByTokenHash(anyString()))
        .thenReturn(Mono.just(refreshToken(true, LocalDateTime.now().plusDays(1))));

    StepVerifier.create(authService.refresh("un-token-de-refresco"))
        .expectError(InvalidRefreshTokenException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza un token de refresco caducado")
  void rejectsExpiredRefreshToken() {
    when(refreshTokenRepository.findByTokenHash(anyString()))
        .thenReturn(Mono.just(refreshToken(false, LocalDateTime.now().minusMinutes(1))));

    StepVerifier.create(authService.refresh("un-token-de-refresco"))
        .expectError(InvalidRefreshTokenException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza un token de refresco desconocido")
  void rejectsUnknownRefreshToken() {
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.empty());

    StepVerifier.create(authService.refresh("un-token-de-refresco"))
        .expectError(InvalidRefreshTokenException.class)
        .verify();
  }

  @Test
  @DisplayName("Revoca el token de refresco al cerrar sesión")
  void revokesTokenOnLogout() {
    RefreshToken stored = refreshToken(false, LocalDateTime.now().plusDays(1));
    when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.just(stored));

    StepVerifier.create(authService.logout("un-token-de-refresco")).verifyComplete();

    assertTrue(stored.isRevoked());
  }

  @Test
  @DisplayName("Guarda solo el hash del token de refresco, nunca el token en claro")
  void storesOnlyTheRefreshTokenHash() {
    when(userRepository.findByEmailIgnoreCase(EMAIL))
        .thenReturn(Mono.just(existingUser(passwordEncoder.encode(PASSWORD))));

    StepVerifier.create(authService.login(loginRequest(PASSWORD)))
        .assertNext(auth -> {
          ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
          verify(refreshTokenRepository).save(captor.capture());
          assertEquals(64, captor.getValue().getTokenHash().length());
          assertNotEquals(auth.getRefreshToken(), captor.getValue().getTokenHash());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Cambia el nombre con el que el usuario se presenta")
  void updatesDisplayName() {
    User user = existingUser(passwordEncoder.encode(PASSWORD));
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user));
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    StepVerifier.create(authService.updateUser(USER_ID, updateRequest("  Sebastián R.  ")))
        // De paso se comprueba que el nombre se guarda sin los espacios de los extremos.
        .assertNext(updated -> assertEquals("Sebastián R.", updated.getDisplayName()))
        .verifyComplete();

    assertEquals("Sebastián R.", user.getDisplayName());
  }

  @Test
  @DisplayName("Deja la cuenta sin nombre cuando se envía en blanco")
  void clearsDisplayNameWhenBlank() {
    User user = existingUser(passwordEncoder.encode(PASSWORD));
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user));
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    StepVerifier.create(authService.updateUser(USER_ID, updateRequest("   ")))
        .assertNext(updated -> assertNull(updated.getDisplayName()))
        .verifyComplete();

    assertNull(user.getDisplayName());
  }

  @Test
  @DisplayName("Un campo ausente no cambia nada, ni toca el correo")
  void leavesAbsentFieldsAlone() {
    User user = existingUser(passwordEncoder.encode(PASSWORD));
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user));
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    StepVerifier.create(authService.updateUser(USER_ID, new UpdateUserRequest()))
        .assertNext(updated -> {
          assertEquals("Sebastian", updated.getDisplayName());
          assertEquals(EMAIL, updated.getEmail());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("No deja cambiar los datos de un usuario que no existe")
  void rejectsUpdateOnUnknownUser() {
    when(userRepository.findById(anyLong())).thenReturn(Mono.empty());

    StepVerifier.create(authService.updateUser(USER_ID, updateRequest("Quien sea")))
        .expectError(InvalidCredentialsException.class)
        .verify();

    verify(userRepository, never()).save(any(User.class));
  }

  /**
   * Construye una petición de cambio con el nombre indicado.
   *
   * @param displayName nombre a guardar
   * @return la petición de cambio
   */
  private UpdateUserRequest updateRequest(String displayName) {
    UpdateUserRequest request = new UpdateUserRequest();
    request.setDisplayName(displayName);
    return request;
  }

  /**
   * Configura el alta de un correo que todavía no existe, devolviendo el usuario ya
   * identificado al guardarlo.
   */
  private void givenNoUserWithEmail() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Mono.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
      User user = invocation.getArgument(0);
      user.setId(USER_ID);
      return Mono.just(user);
    });
  }

  /**
   * Construye una petición de alta con el correo y la contraseña de prueba.
   *
   * @return la petición de alta
   */
  private RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD);
    request.setDisplayName("Sebastian");
    return request;
  }

  /**
   * Construye una petición de acceso con la contraseña indicada.
   *
   * @param password contraseña con la que se intenta acceder
   * @return la petición de acceso
   */
  private LoginRequest loginRequest(String password) {
    return new LoginRequest(EMAIL, password);
  }

  /**
   * Construye el usuario ya persistido que usan las pruebas.
   *
   * @param passwordHash contraseña cifrada, o null si la cuenta está solo sembrada
   * @return el usuario existente
   */
  private User existingUser(String passwordHash) {
    return new User(USER_ID, EMAIL, passwordHash, "Sebastian", true, LocalDateTime.now());
  }

  /**
   * Construye un token de refresco persistido con el estado indicado.
   *
   * @param revoked   indica si el token ya fue consumido
   * @param expiresAt momento en que el token caduca
   * @return el token de refresco
   */
  private RefreshToken refreshToken(boolean revoked, LocalDateTime expiresAt) {
    return new RefreshToken(1L, USER_ID, "hash", expiresAt, revoked, LocalDateTime.now());
  }
}
