package com.sreyes.finscope.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Política de bloqueo temporal ante intentos de acceso fallidos.
 * El bloqueo siempre caduca solo y crece de forma progresiva: así se encarece adivinar una
 * contraseña sin dar a un tercero la posibilidad de dejar fuera a alguien indefinidamente
 * limitándose a fallar en su nombre.
 *
 * @param enabled     si la política debe aplicarse
 * @param maxAttempts fallos consecutivos admitidos antes del primer bloqueo
 * @param initialLock duración del primer bloqueo
 * @param maxLock     duración máxima que puede alcanzar el bloqueo
 */
@ConfigurationProperties(prefix = "finscope.security.login-attempts")
public record LoginAttemptProperties(
    boolean enabled,
    int maxAttempts,
    Duration initialLock,
    Duration maxLock) {

  /**
   * Aplica los valores por defecto y comprueba que la política es utilizable.
   */
  public LoginAttemptProperties {
    maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
    initialLock = initialLock == null ? Duration.ofSeconds(30) : initialLock;
    maxLock = maxLock == null ? Duration.ofMinutes(15) : maxLock;
    if (initialLock.isZero() || initialLock.isNegative()) {
      throw new IllegalStateException(
          "finscope.security.login-attempts.initial-lock must be a positive duration");
    }
    if (maxLock.compareTo(initialLock) < 0) {
      throw new IllegalStateException(
          "finscope.security.login-attempts.max-lock must not be shorter than initial-lock");
    }
  }
}
