package com.sreyes.finscope.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orígenes que pueden consumir la API desde un navegador.
 * La lista se declara siempre de forma explícita: el cliente envía el token en una
 * cabecera, no en una cookie, pero un comodín permitiría a cualquier página leer las
 * respuestas de la API con el token que le haya robado a la aplicación legítima.
 *
 * @param allowedOrigins orígenes autorizados, sin barra final
 * @param maxAge         tiempo que el navegador puede cachear la respuesta preflight
 */
@ConfigurationProperties(prefix = "finscope.security.cors")
public record CorsProperties(List<String> allowedOrigins, Duration maxAge) {

  /**
   * Métodos que la API llega a usar. No se abre ninguno más.
   */
  public static final List<String> ALLOWED_METHODS =
      List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

  /**
   * Cabeceras que el cliente necesita enviar: el token y el tipo de contenido.
   */
  public static final List<String> ALLOWED_HEADERS =
      List.of("Authorization", "Content-Type", "Accept", "X-Request-ID");

  /**
   * Cabeceras que el navegador deja leer al cliente además de las seguras por defecto.
   */
  public static final List<String> EXPOSED_HEADERS = List.of("X-Request-ID", "Retry-After");

  /**
   * Aplica los valores por defecto cuando la configuración no los aporta.
   */
  public CorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
  }
}
