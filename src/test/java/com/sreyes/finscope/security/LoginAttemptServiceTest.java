package com.sreyes.finscope.security;

import com.sreyes.finscope.exception.custom.TooManyAttemptsException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Pruebas de la política de bloqueo temporal ante intentos de acceso fallidos, con especial
 * atención a que el bloqueo caduque solo: si no lo hiciera, cualquiera podría dejar fuera a
 * otra persona limitándose a fallar en su nombre.
 */
class LoginAttemptServiceTest {

  private static final String EMAIL = "sebastian@example.com";

  @Test
  @DisplayName("Deja intentar mientras no se agoten los intentos admitidos")
  void allowsAttemptsBelowTheLimit() {
    LoginAttemptService service = service(Duration.ofMinutes(5));

    for (int attempt = 0; attempt < 4; attempt++) {
      service.recordFailure(EMAIL);
    }

    StepVerifier.create(service.requireNotLocked(EMAIL)).verifyComplete();
  }

  @Test
  @DisplayName("Bloquea al agotar los intentos admitidos")
  void locksAfterTooManyFailures() {
    LoginAttemptService service = service(Duration.ofMinutes(5));

    for (int attempt = 0; attempt < 5; attempt++) {
      service.recordFailure(EMAIL);
    }

    StepVerifier.create(service.requireNotLocked(EMAIL))
        .expectError(TooManyAttemptsException.class)
        .verify();
  }

  @Test
  @DisplayName("No distingue mayúsculas ni espacios al contar los fallos")
  void countsFailuresRegardlessOfHowTheEmailIsWritten() {
    LoginAttemptService service = service(Duration.ofMinutes(5));

    for (int attempt = 0; attempt < 5; attempt++) {
      service.recordFailure("  SEBASTIAN@Example.com ");
    }

    StepVerifier.create(service.requireNotLocked(EMAIL))
        .expectError(TooManyAttemptsException.class)
        .verify();
  }

  @Test
  @DisplayName("Un acceso correcto olvida los fallos acumulados")
  void successClearsTheCounter() {
    LoginAttemptService service = service(Duration.ofMinutes(5));

    for (int attempt = 0; attempt < 5; attempt++) {
      service.recordFailure(EMAIL);
    }
    service.recordSuccess(EMAIL);

    StepVerifier.create(service.requireNotLocked(EMAIL)).verifyComplete();
  }

  @Test
  @DisplayName("El bloqueo caduca solo y no deja la cuenta fuera para siempre")
  void lockExpiresOnItsOwn() {
    // Con un bloqueo de un milisegundo la espera se agota antes de comprobarla, que es lo
    // que se quiere verificar: que el bloqueo tiene fin sin que nadie lo levante.
    LoginAttemptService service = service(Duration.ofMillis(1));

    for (int attempt = 0; attempt < 10; attempt++) {
      service.recordFailure(EMAIL);
    }
    awaitLockExpiry();

    StepVerifier.create(service.requireNotLocked(EMAIL)).verifyComplete();
  }

  @Test
  @DisplayName("No bloquea nada cuando la política está desactivada")
  void doesNothingWhenDisabled() {
    LoginAttemptService service = new LoginAttemptService(new LoginAttemptProperties(
        false, 1, Duration.ofMinutes(5), Duration.ofMinutes(5)));

    service.recordFailure(EMAIL);
    service.recordFailure(EMAIL);

    StepVerifier.create(service.requireNotLocked(EMAIL)).verifyComplete();
  }

  /**
   * Espera a que el reloj avance lo suficiente como para que el bloqueo mínimo haya
   * vencido. Los diez fallos se registran dentro del mismo milisegundo, así que sin este
   * margen la comprobación caería aún dentro de la espera.
   */
  private void awaitLockExpiry() {
    long deadline = System.currentTimeMillis() + 5L;
    while (System.currentTimeMillis() < deadline) {
      Thread.onSpinWait();
    }
  }

  /**
   * Construye el servicio con cinco intentos admitidos y la espera indicada.
   *
   * @param lock duración del bloqueo, usada también como tope
   * @return el servicio configurado
   */
  private LoginAttemptService service(Duration lock) {
    return new LoginAttemptService(new LoginAttemptProperties(true, 5, lock, lock));
  }
}
