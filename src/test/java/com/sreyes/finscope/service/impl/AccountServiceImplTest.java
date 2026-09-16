package com.sreyes.finscope.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.sreyes.finscope.security.LoginAttemptProperties;
import com.sreyes.finscope.security.LoginAttemptService;
import com.sreyes.finscope.security.MailProperties;
import com.sreyes.finscope.security.SecureTokens;
import com.sreyes.finscope.service.MailService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * Pruebas unitarias de {@link AccountServiceImpl}, centradas en el ciclo de vida de los
 * enlaces que se mandan por correo: qué se guarda de ellos, cuándo dejan de valer y qué
 * cambia en la cuenta al consumirlos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final String EMAIL = "sebastian@example.com";
  private static final String NEW_EMAIL = "nuevo@example.com";
  private static final String PASSWORD = "una-contrasena-larga";
  private static final String LINK = "link-recibido-por-correo";

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserIdentityRepository userIdentityRepository;

  @Mock
  private AccountTokenRepository accountTokenRepository;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private MailService mailService;

  private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  private AccountServiceImpl accountService;

  @BeforeEach
  void setUp() {
    MailProperties mailProperties = new MailProperties("FinScope <no-reply@finscope.test>",
        "https://app.finscope.test", Duration.ofHours(24), Duration.ofHours(1),
        Duration.ofHours(1));
    accountService = new AccountServiceImpl(userRepository, userIdentityRepository,
        accountTokenRepository, refreshTokenRepository, mailService, mailProperties,
        passwordEncoder,
        new LoginAttemptService(
            new LoginAttemptProperties(true, 5, Duration.ofSeconds(30), Duration.ofMinutes(15))),
        Clock.systemDefaultZone(), new ContextTransactionalOperator());

    when(accountTokenRepository.deleteByUserIdAndPurpose(anyLong(), anyString()))
        .thenReturn(Mono.just(0L));
    when(accountTokenRepository.save(any(AccountToken.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(userRepository.save(any(User.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(userIdentityRepository.save(any(UserIdentity.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(refreshTokenRepository.revokeAllByUserId(anyLong())).thenReturn(Mono.just(1L));
    when(mailService.sendEmailVerification(anyString(), any(), anyString()))
        .thenReturn(Mono.empty());
    when(mailService.sendPasswordReset(anyString(), any(), anyString()))
        .thenReturn(Mono.empty());
    when(mailService.sendEmailChange(anyString(), any(), anyString())).thenReturn(Mono.empty());
    when(mailService.sendEmailChangeNotice(anyString(), any(), anyString()))
        .thenReturn(Mono.empty());
  }

  @Test
  @DisplayName("Manda el enlace de verificación y guarda solo su hash")
  void sendsVerificationStoringOnlyTheHash() {
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(false)));

    StepVerifier.create(accountService.sendEmailVerification(USER_ID)).verifyComplete();

    ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
    verify(mailService).sendEmailVerification(eq(EMAIL), any(), sent.capture());
    AccountToken stored = savedToken();
    assertEquals(AccountToken.EMAIL_VERIFICATION, stored.getPurpose());
    assertNotEquals(sent.getValue(), stored.getTokenHash());
    assertEquals(SecureTokens.hash(sent.getValue()), stored.getTokenHash());
    // El de verificar no lleva dirección de destino: la cuenta ya está en la suya.
    assertNull(stored.getTargetEmail());
  }

  @Test
  @DisplayName("Descarta el enlace anterior antes de emitir otro")
  void discardsThePreviousLinkOfTheSamePurpose() {
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(false)));

    StepVerifier.create(accountService.sendEmailVerification(USER_ID)).verifyComplete();

    verify(accountTokenRepository)
        .deleteByUserIdAndPurpose(USER_ID, AccountToken.EMAIL_VERIFICATION);
  }

  @Test
  @DisplayName("No manda nada si el correo ya está verificado")
  void skipsVerificationWhenAlreadyVerified() {
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(true)));

    StepVerifier.create(accountService.sendEmailVerification(USER_ID)).verifyComplete();

    verify(mailService, never()).sendEmailVerification(anyString(), any(), anyString());
    verify(accountTokenRepository, never()).save(any(AccountToken.class));
  }

  @Test
  @DisplayName("Da por verificado el correo al consumir el enlace")
  void confirmsEmailVerification() {
    givenToken(AccountToken.EMAIL_VERIFICATION, null, LocalDateTime.now().plusHours(1), null);
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(false)));

    StepVerifier.create(accountService.confirmEmailVerification(LINK)).verifyComplete();

    assertTrue(savedUser().isEmailVerified());
    assertNotNullConsumedAt();
  }

  @Test
  @DisplayName("Rechaza un enlace caducado")
  void rejectsExpiredLink() {
    givenToken(AccountToken.EMAIL_VERIFICATION, null, LocalDateTime.now().minusMinutes(1), null);

    StepVerifier.create(accountService.confirmEmailVerification(LINK))
        .expectError(InvalidAccountTokenException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza un enlace ya usado")
  void rejectsAlreadyUsedLink() {
    givenToken(AccountToken.EMAIL_VERIFICATION, null, LocalDateTime.now().plusHours(1),
        LocalDateTime.now().minusMinutes(5));

    StepVerifier.create(accountService.confirmEmailVerification(LINK))
        .expectError(InvalidAccountTokenException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza un enlace emitido para otra cosa")
  void rejectsLinkOfAnotherPurpose() {
    givenToken(AccountToken.EMAIL_VERIFICATION, null, LocalDateTime.now().plusHours(1), null);

    // El enlace existe y está vigente, pero es el de verificar el correo: no debe servir
    // para establecer una contraseña nueva aunque los dos salgan del mismo buzón.
    StepVerifier.create(accountService.resetPassword(resetRequest()))
        .expectError(InvalidAccountTokenException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza un enlace desconocido")
  void rejectsUnknownLink() {
    when(accountTokenRepository.findByTokenHash(anyString())).thenReturn(Mono.empty());

    StepVerifier.create(accountService.confirmEmailVerification(LINK))
        .expectError(InvalidAccountTokenException.class)
        .verify();
  }

  @Test
  @DisplayName("El cambio de correo exige la contraseña en curso")
  void rejectsEmailChangeWithWrongPassword() {
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(true)));

    StepVerifier.create(accountService.requestEmailChange(USER_ID,
            changeRequest(NEW_EMAIL, "otra-contrasena")))
        .expectError(InvalidCredentialsException.class)
        .verify();

    verify(mailService, never()).sendEmailChange(anyString(), any(), anyString());
  }

  @Test
  @DisplayName("El cambio de correo rechaza una dirección que ya tiene cuenta")
  void rejectsEmailChangeToATakenAddress() {
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(true)));
    when(userRepository.findByEmailIgnoreCase(NEW_EMAIL)).thenReturn(Mono.just(otherUser()));

    StepVerifier.create(accountService.requestEmailChange(USER_ID,
            changeRequest(NEW_EMAIL, PASSWORD)))
        .expectError(EmailAlreadyRegisteredException.class)
        .verify();
  }

  @Test
  @DisplayName("El cambio de correo rechaza la dirección que la cuenta ya tiene")
  void rejectsEmailChangeToTheSameAddress() {
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(true)));

    StepVerifier.create(accountService.requestEmailChange(USER_ID,
            changeRequest(EMAIL.toUpperCase(), PASSWORD)))
        .expectError(EmailAlreadyRegisteredException.class)
        .verify();
  }

  @Test
  @DisplayName("Pedir el cambio no toca el correo todavía y avisa al anterior")
  void requestingAnEmailChangeLeavesTheAccountUntouched() {
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(true)));
    when(userRepository.findByEmailIgnoreCase(NEW_EMAIL)).thenReturn(Mono.empty());

    StepVerifier.create(accountService.requestEmailChange(USER_ID,
        changeRequest(NEW_EMAIL, PASSWORD))).verifyComplete();

    // El correo de la cuenta sigue siendo el de siempre: una dirección mal escrita se queda
    // en un enlace que caduca, no en una cuenta a la que ya no se puede entrar.
    verify(userRepository, never()).save(any(User.class));
    verify(mailService).sendEmailChange(eq(NEW_EMAIL), any(), anyString());
    verify(mailService).sendEmailChangeNotice(eq(EMAIL), any(), eq(NEW_EMAIL));
    AccountToken stored = savedToken();
    assertEquals(AccountToken.EMAIL_CHANGE, stored.getPurpose());
    assertEquals(NEW_EMAIL, stored.getTargetEmail());
  }

  @Test
  @DisplayName("Confirmar el cambio mueve el correo, lo da por verificado y lleva la identidad")
  void confirmsEmailChange() {
    givenToken(AccountToken.EMAIL_CHANGE, NEW_EMAIL, LocalDateTime.now().plusHours(1), null);
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(false)));
    when(userRepository.findByEmailIgnoreCase(NEW_EMAIL)).thenReturn(Mono.empty());
    when(userIdentityRepository.findByProviderAndSubject(UserIdentity.LOCAL_PROVIDER, EMAIL))
        .thenReturn(Mono.just(localIdentity()));

    StepVerifier.create(accountService.confirmEmailChange(LINK)).verifyComplete();

    User saved = savedUser();
    assertEquals(NEW_EMAIL, saved.getEmail());
    assertTrue(saved.isEmailVerified());
    ArgumentCaptor<UserIdentity> identity = ArgumentCaptor.forClass(UserIdentity.class);
    verify(userIdentityRepository).save(identity.capture());
    assertEquals(NEW_EMAIL, identity.getValue().getSubject());
  }

  @Test
  @DisplayName("El cambio de correo mueve cuenta e identidad juntas; el enlace se quema aparte")
  void confirmsEmailChangeAtomically() {
    Map<String, Boolean> inTransaction = new ConcurrentHashMap<>();
    givenToken(AccountToken.EMAIL_CHANGE, NEW_EMAIL, LocalDateTime.now().plusHours(1), null);
    recordSaves(inTransaction);
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(false)));
    when(userRepository.findByEmailIgnoreCase(NEW_EMAIL)).thenReturn(Mono.empty());
    when(userIdentityRepository.findByProviderAndSubject(UserIdentity.LOCAL_PROVIDER, EMAIL))
        .thenReturn(Mono.just(localIdentity()));
    when(userIdentityRepository.save(any(UserIdentity.class))).thenAnswer(invocation ->
        Mono.deferContextual(context -> {
          inTransaction.put("identity", ContextTransactionalOperator.inTransaction(context));
          return Mono.just(invocation.getArgument(0));
        }));

    StepVerifier.create(accountService.confirmEmailChange(LINK)).verifyComplete();

    assertEquals(Map.of("link", false, "user", true, "identity", true), inTransaction);
  }

  @Test
  @DisplayName("Confirmar el cambio falla si la dirección se ocupó mientras tanto")
  void rejectsEmailChangeTakenMeanwhile() {
    givenToken(AccountToken.EMAIL_CHANGE, NEW_EMAIL, LocalDateTime.now().plusHours(1), null);
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(false)));
    when(userRepository.findByEmailIgnoreCase(NEW_EMAIL)).thenReturn(Mono.just(otherUser()));

    StepVerifier.create(accountService.confirmEmailChange(LINK))
        .expectError(EmailAlreadyRegisteredException.class)
        .verify();
  }

  @Test
  @DisplayName("Pedir recuperar la contraseña de un correo sin cuenta responde igual")
  void keepsQuietAboutUnknownAddresses() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Mono.empty());

    StepVerifier.create(accountService.requestPasswordReset(EMAIL)).verifyComplete();

    verify(mailService, never()).sendPasswordReset(anyString(), any(), anyString());
  }

  @Test
  @DisplayName("Una cuenta sin contraseña no recibe enlace de recuperación")
  void skipsSeededAccounts() {
    User seeded = user(false);
    seeded.setPasswordHash(null);
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Mono.just(seeded));

    StepVerifier.create(accountService.requestPasswordReset(EMAIL)).verifyComplete();

    verify(mailService, never()).sendPasswordReset(anyString(), any(), anyString());
  }

  @Test
  @DisplayName("Manda el enlace de recuperación a una cuenta utilizable")
  void sendsPasswordResetLink() {
    when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Mono.just(user(true)));

    StepVerifier.create(accountService.requestPasswordReset(" " + EMAIL + " "))
        .verifyComplete();

    verify(mailService).sendPasswordReset(eq(EMAIL), any(), anyString());
    assertEquals(AccountToken.PASSWORD_RESET, savedToken().getPurpose());
  }

  @Test
  @DisplayName("Restablecer la contraseña la cambia, verifica el correo y cierra las sesiones")
  void resetsPassword() {
    givenToken(AccountToken.PASSWORD_RESET, null, LocalDateTime.now().plusHours(1), null);
    User existing = user(false);
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(existing));

    StepVerifier.create(accountService.resetPassword(resetRequest())).verifyComplete();

    User saved = savedUser();
    assertTrue(passwordEncoder.matches("contrasena-nueva", saved.getPasswordHash()));
    assertFalse(passwordEncoder.matches(PASSWORD, saved.getPasswordHash()));
    assertTrue(saved.isEmailVerified());
    verify(refreshTokenRepository).revokeAllByUserId(USER_ID);
  }

  @Test
  @DisplayName("La contraseña nueva y el cierre de sesiones van juntos; el enlace se quema aparte")
  void resetsPasswordAtomically() {
    Map<String, Boolean> inTransaction = new ConcurrentHashMap<>();
    givenToken(AccountToken.PASSWORD_RESET, null, LocalDateTime.now().plusHours(1), null);
    recordSaves(inTransaction);
    when(userRepository.findById(USER_ID)).thenReturn(Mono.just(user(false)));
    when(refreshTokenRepository.revokeAllByUserId(anyLong())).thenReturn(
        Mono.deferContextual(context -> {
          inTransaction.put("sessions", ContextTransactionalOperator.inTransaction(context));
          return Mono.just(1L);
        }));

    StepVerifier.create(accountService.resetPassword(resetRequest())).verifyComplete();

    assertEquals(Map.of("link", false, "user", true, "sessions", true), inTransaction);
  }

  /**
   * Anota, para el enlace consumido y el usuario guardado, si se escribieron dentro de la
   * transacción.
   *
   * @param calls dónde anotar cada escritura
   */
  private void recordSaves(Map<String, Boolean> calls) {
    when(accountTokenRepository.save(any(AccountToken.class))).thenAnswer(invocation ->
        Mono.deferContextual(context -> {
          calls.put("link", ContextTransactionalOperator.inTransaction(context));
          return Mono.just(invocation.getArgument(0));
        }));
    when(userRepository.save(any(User.class))).thenAnswer(invocation ->
        Mono.deferContextual(context -> {
          calls.put("user", ContextTransactionalOperator.inTransaction(context));
          return Mono.just(invocation.getArgument(0));
        }));
  }

  /**
   * Prepara la consulta del enlace con el estado indicado.
   *
   * @param purpose     propósito del enlace
   * @param targetEmail dirección de destino, solo en el cambio de correo
   * @param expiresAt   cuándo caduca
   * @param consumedAt  cuándo se usó, o nulo si sigue sin usarse
   */
  private void givenToken(String purpose, String targetEmail, LocalDateTime expiresAt,
                          LocalDateTime consumedAt) {
    AccountToken token = new AccountToken(1L, USER_ID, purpose, SecureTokens.hash(LINK),
        targetEmail, expiresAt, consumedAt, LocalDateTime.now());
    when(accountTokenRepository.findByTokenHash(SecureTokens.hash(LINK)))
        .thenReturn(Mono.just(token));
  }

  /**
   * Construye el usuario que usan las pruebas.
   *
   * @param verified si su correo ya está verificado
   * @return el usuario existente
   */
  private User user(boolean verified) {
    return new User(USER_ID, EMAIL, passwordEncoder.encode(PASSWORD), verified, "Sebastian",
        true, LocalDateTime.now());
  }

  /**
   * Construye la cuenta de otra persona, la que ocupa una dirección.
   *
   * @return el otro usuario
   */
  private User otherUser() {
    return new User(99L, NEW_EMAIL, "hash", true, null, true, LocalDateTime.now());
  }

  /**
   * Construye la identidad local del usuario con su correo de siempre.
   *
   * @return la identidad local
   */
  private UserIdentity localIdentity() {
    return new UserIdentity(3L, USER_ID, UserIdentity.LOCAL_PROVIDER, EMAIL);
  }

  private ChangeEmailRequest changeRequest(String email, String password) {
    return new ChangeEmailRequest(email, password);
  }

  private ResetPasswordRequest resetRequest() {
    return new ResetPasswordRequest(LINK, "contrasena-nueva");
  }

  /**
   * Recupera el usuario tal y como quedó guardado.
   *
   * @return el usuario que recibió el repositorio
   */
  private User savedUser() {
    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    return captor.getValue();
  }

  /**
   * Recupera el enlace tal y como quedó guardado al emitirse.
   *
   * @return el enlace que recibió el repositorio
   */
  private AccountToken savedToken() {
    ArgumentCaptor<AccountToken> captor = ArgumentCaptor.forClass(AccountToken.class);
    verify(accountTokenRepository).save(captor.capture());
    return captor.getValue();
  }

  /**
   * Comprueba que el enlace quedó marcado como consumido.
   */
  private void assertNotNullConsumedAt() {
    ArgumentCaptor<AccountToken> captor = ArgumentCaptor.forClass(AccountToken.class);
    verify(accountTokenRepository).save(captor.capture());
    assertTrue(captor.getValue().getConsumedAt() != null,
        "el enlace debe quedar marcado como consumido");
  }
}
