package com.sreyes.finscope.security.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de peticiones aplicados por origen.
 * Se separan dos cupos porque protegen de cosas distintas: el de autenticación frena el
 * ensayo de credenciales, mientras que el general solo evita que un cliente sature la API.
 * Aplicar el mismo número a los dos obligaría a elegir entre dejar pasar miles de intentos
 * de acceso o impedir el uso normal de la aplicación.
 *
 * @param enabled si el filtro debe aplicarse
 * @param auth    cupo de las rutas de autenticación
 * @param api     cupo del resto de la API
 */
@ConfigurationProperties(prefix = "finscope.security.rate-limit")
public record RateLimitProperties(boolean enabled, Bucket auth, Bucket api) {

  /**
   * Aplica los valores por defecto cuando la configuración no los aporta.
   */
  public RateLimitProperties {
    auth = auth == null ? new Bucket(20, Duration.ofMinutes(1)) : auth;
    api = api == null ? new Bucket(300, Duration.ofMinutes(1)) : api;
  }

  /**
   * Cupo de peticiones permitido dentro de una ventana de tiempo.
   *
   * @param capacity peticiones admitidas por ventana
   * @param window   duración de la ventana
   */
  public record Bucket(int capacity, Duration window) {

    /**
     * Comprueba que el cupo tiene sentido antes de arrancar.
     */
    public Bucket {
      if (capacity <= 0) {
        throw new IllegalStateException("Rate limit capacity must be greater than zero");
      }
      if (window == null || window.isZero() || window.isNegative()) {
        throw new IllegalStateException("Rate limit window must be a positive duration");
      }
    }
  }
}
