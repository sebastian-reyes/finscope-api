package com.sreyes.finscope.security.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contador de peticiones por clave dentro de una ventana de tiempo fija.
 * Se resuelve en memoria y sin bloqueos porque el filtro que lo consulta corre sobre el
 * bucle de eventos: una consulta remota ahí costaría más que la propia petición que se
 * quiere limitar.
 *
 * <p>Al vivir en el proceso, el cupo es por instancia de la aplicación. Con varias
 * réplicas el límite efectivo se multiplica por su número, por lo que en producción este
 * filtro es una defensa más y no la única: el límite de borde corresponde al proxy
 * inverso, la pasarela o el WAF que tenga delante.</p>
 */
public class FixedWindowRateLimiter {

  /**
   * Número de claves a partir del cual se purgan las ventanas ya caducadas.
   * Sin este techo el mapa crecería con cada dirección distinta que llegase, que es justo
   * lo que un atacante puede provocar rotando de origen.
   */
  private static final int CLEANUP_THRESHOLD = 10_000;

  /**
   * Separación mínima entre purgas. Recorrer el mapa en cada petición una vez superado el
   * techo convertiría la propia limitación en la forma de saturar el servidor.
   */
  private static final long CLEANUP_INTERVAL_MILLIS = 10_000L;

  private final int capacity;
  private final long windowMillis;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();
  private final AtomicLong lastCleanup = new AtomicLong();

  /**
   * Crea un contador con el cupo indicado.
   *
   * @param capacity peticiones admitidas por ventana
   * @param window   duración de la ventana
   */
  public FixedWindowRateLimiter(int capacity, Duration window) {
    this.capacity = capacity;
    this.windowMillis = window.toMillis();
  }

  /**
   * Registra una petición de la clave indicada y decide si puede atenderse.
   *
   * @param key       identificador del origen al que se aplica el cupo
   * @param nowMillis instante actual en milisegundos
   * @return el resultado de la comprobación
   */
  public Decision tryConsume(String key, long nowMillis) {
    cleanupIfNeeded(nowMillis);
    Window window = windows.compute(key, (ignored, current) ->
        current == null || current.isExpired(nowMillis, windowMillis)
            ? new Window(nowMillis)
            : current);
    int used = window.count().incrementAndGet();
    long retryAfterSeconds =
        Math.max(1L, (window.startedAt() + windowMillis - nowMillis + 999L) / 1000L);
    return new Decision(used <= capacity, retryAfterSeconds);
  }

  /**
   * Descarta las ventanas ya caducadas cuando el mapa ha crecido y ha pasado el intervalo
   * mínimo desde la última purga.
   *
   * @param nowMillis instante actual en milisegundos
   */
  private void cleanupIfNeeded(long nowMillis) {
    if (windows.size() <= CLEANUP_THRESHOLD) {
      return;
    }
    long previous = lastCleanup.get();
    if (nowMillis - previous < CLEANUP_INTERVAL_MILLIS
        || !lastCleanup.compareAndSet(previous, nowMillis)) {
      return;
    }
    windows.values().removeIf(window -> window.isExpired(nowMillis, windowMillis));
  }

  /**
   * Olvida el cupo consumido por una clave.
   *
   * @param key identificador del origen
   */
  public void reset(String key) {
    windows.remove(key);
  }

  /**
   * Resultado de comprobar el cupo de una clave.
   *
   * @param allowed           si la petición puede atenderse
   * @param retryAfterSeconds segundos que faltan para que se abra la siguiente ventana
   */
  public record Decision(boolean allowed, long retryAfterSeconds) {
  }

  /**
   * Ventana abierta para una clave, con las peticiones que lleva contadas.
   *
   * @param startedAt instante en el que se abrió
   * @param count     peticiones registradas en ella
   */
  private record Window(long startedAt, AtomicInteger count) {

    Window(long startedAt) {
      this(startedAt, new AtomicInteger());
    }

    boolean isExpired(long nowMillis, long windowMillis) {
      return nowMillis - startedAt >= windowMillis;
    }
  }
}
