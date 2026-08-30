package com.sreyes.finscope.security;

import com.sreyes.finscope.exception.custom.TooManyAttemptsException;
import com.sreyes.finscope.util.constants.Constants;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Frena el ensayo de contraseñas sobre una misma cuenta.
 * El límite por origen del filtro de peticiones no basta aquí: reparte los intentos entre
 * varias direcciones y el cupo deja de notarse, mientras que la cuenta atacada sigue siendo
 * la misma. Por eso el recuento se lleva por correo.
 *
 * <p>El bloqueo se aplica sobre el correo recibido exista o no la cuenta, de modo que la
 * respuesta no revela cuáles están dadas de alta, y caduca por sí solo con una espera que
 * se dobla en cada fallo hasta un tope. Un acceso correcto lo olvida.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

  /**
   * Número de fallos a partir del cual dejar de alargar la espera, para que el
   * desplazamiento no desborde. Con el tope de duración configurado se alcanza mucho antes.
   */
  private static final int MAX_BACKOFF_STEPS = 20;

  /**
   * Número de cuentas vigiladas a partir del cual se descartan los registros ya caducados.
   */
  private static final int CLEANUP_THRESHOLD = 10_000;

  /**
   * Separación mínima entre purgas. Sin ella, un atacante que llenase el mapa con correos
   * distintos haría que cada intento posterior recorriera todo lo acumulado.
   */
  private static final long CLEANUP_INTERVAL_MILLIS = 10_000L;

  private final LoginAttemptProperties properties;
  private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
  private final AtomicLong lastCleanup = new AtomicLong();

  /**
   * Comprueba que la cuenta no está bloqueada antes de verificar la contraseña.
   *
   * @param email correo con el que se intenta acceder
   * @return Mono vacío si puede intentarse, o un error si el acceso está bloqueado
   */
  public Mono<Void> requireNotLocked(String email) {
    if (!properties.enabled()) {
      return Mono.empty();
    }
    Attempts current = attempts.get(key(email));
    if (current != null && current.lockedUntil() > System.currentTimeMillis()) {
      return Mono.error(new TooManyAttemptsException(Constants.TOO_MANY_ATTEMPTS));
    }
    return Mono.empty();
  }

  /**
   * Registra un intento fallido y amplía el bloqueo si procede.
   *
   * @param email correo con el que se ha intentado acceder
   */
  public void recordFailure(String email) {
    if (!properties.enabled()) {
      return;
    }
    String key = key(email);
    long now = System.currentTimeMillis();
    cleanupIfNeeded(now);
    Attempts updated = attempts.compute(key, (ignored, current) -> {
      int failures = current == null || current.expiresAt() <= now ? 1 : current.failures() + 1;
      long lock = lockMillis(failures);
      return new Attempts(failures, now + lock, now + Math.max(lock, counterWindowMillis()));
    });
    if (updated.failures() >= properties.maxAttempts()) {
      log.warn("Login temporarily locked after {} failed attempts", updated.failures());
    }
  }

  /**
   * Descarta los recuentos ya caducados cuando el mapa ha crecido y ha pasado el intervalo
   * mínimo desde la última purga.
   *
   * @param nowMillis instante actual en milisegundos
   */
  private void cleanupIfNeeded(long nowMillis) {
    if (attempts.size() <= CLEANUP_THRESHOLD) {
      return;
    }
    long previous = lastCleanup.get();
    if (nowMillis - previous < CLEANUP_INTERVAL_MILLIS
        || !lastCleanup.compareAndSet(previous, nowMillis)) {
      return;
    }
    attempts.values().removeIf(entry -> entry.expiresAt() <= nowMillis);
  }

  /**
   * Olvida los fallos acumulados tras un acceso correcto.
   *
   * @param email correo con el que se ha accedido
   */
  public void recordSuccess(String email) {
    attempts.remove(key(email));
  }

  /**
   * Calcula cuánto dura el bloqueo tras el número de fallos indicado.
   * Hasta agotar los intentos admitidos no hay espera; a partir de ahí se dobla en cada
   * fallo sin superar nunca el tope configurado.
   *
   * @param failures fallos consecutivos acumulados
   * @return la duración del bloqueo en milisegundos
   */
  private long lockMillis(int failures) {
    if (failures < properties.maxAttempts()) {
      return 0L;
    }
    int steps = Math.min(failures - properties.maxAttempts(), MAX_BACKOFF_STEPS);
    long millis = properties.initialLock().toMillis() << steps;
    return Math.min(millis, properties.maxLock().toMillis());
  }

  /**
   * Tiempo que sobreviven los fallos acumulados sin que llegue uno nuevo.
   * Se toma del propio tope de bloqueo para no añadir otro valor que configurar: pasado
   * ese rato sin volver a fallar, la cuenta empieza de cero.
   *
   * @return la validez del recuento en milisegundos
   */
  private long counterWindowMillis() {
    return properties.maxLock().toMillis();
  }

  /**
   * Normaliza el correo para que el recuento no dependa de cómo se escriba.
   *
   * @param email correo recibido en la petición
   * @return la clave del recuento
   */
  private String key(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Fallos acumulados por una cuenta y momento en que expira su bloqueo.
   *
   * @param failures    fallos consecutivos
   * @param lockedUntil instante hasta el que no se admiten intentos
   * @param expiresAt   instante en el que el recuento se olvida por inactividad
   */
  private record Attempts(int failures, long lockedUntil, long expiresAt) {
  }
}
