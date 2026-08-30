package com.sreyes.finscope.security.ratelimit;

import com.sreyes.finscope.security.ErrorResponseWriter;
import com.sreyes.finscope.util.constants.Constants;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Limita el número de peticiones que admite la API por origen.
 * Se ejecuta antes de la cadena de seguridad para que el trabajo que descarta —validar la
 * firma de un token o comprobar una contraseña— no llegue a hacerse, que es precisamente
 * lo caro cuando alguien insiste.
 *
 * <p>Las rutas de autenticación llevan su propio cupo, mucho más estrecho, porque son las
 * únicas donde repetir la misma petición tiene sentido para un atacante.</p>
 */
@Slf4j
@Order(RateLimitWebFilter.ORDER)
public class RateLimitWebFilter implements WebFilter {

  /**
   * Posición del filtro en la cadena. Va por delante de la de seguridad, registrada por
   * Spring Security en {@code -100}.
   */
  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

  /**
   * Prefijo de las rutas de autenticación, que son las que reciben el cupo estricto.
   */
  private static final String AUTH_PATH_PREFIX = "/auth/";

  /**
   * Bytes del resumen que se conservan para distinguir credenciales.
   */
  private static final int FINGERPRINT_BYTES = 8;

  private final RateLimitProperties properties;
  private final ErrorResponseWriter errorResponseWriter;
  private final FixedWindowRateLimiter authLimiter;
  private final FixedWindowRateLimiter apiLimiter;

  /**
   * Crea el filtro con los cupos configurados.
   *
   * @param properties          límites configurados
   * @param errorResponseWriter escritor de la respuesta de error
   */
  public RateLimitWebFilter(RateLimitProperties properties,
                            ErrorResponseWriter errorResponseWriter) {
    this.properties = properties;
    this.errorResponseWriter = errorResponseWriter;
    this.authLimiter =
        new FixedWindowRateLimiter(properties.auth().capacity(), properties.auth().window());
    this.apiLimiter =
        new FixedWindowRateLimiter(properties.api().capacity(), properties.api().window());
  }

  @Override
  @NullMarked
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!properties.enabled()) {
      return chain.filter(exchange);
    }
    String path = exchange.getRequest().getPath().value();
    boolean auth = path.startsWith(AUTH_PATH_PREFIX);
    FixedWindowRateLimiter limiter = auth ? authLimiter : apiLimiter;
    FixedWindowRateLimiter.Decision decision =
        limiter.tryConsume(key(exchange, auth), System.currentTimeMillis());
    if (decision.allowed()) {
      return chain.filter(exchange);
    }
    log.warn("Rate limit exceeded for {} {}", exchange.getRequest().getMethod(), path);
    exchange.getResponse().getHeaders()
        .set(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
    return errorResponseWriter.write(exchange, HttpStatus.TOO_MANY_REQUESTS,
        "RATE_LIMIT_EXCEEDED", Constants.RATE_LIMIT_EXCEEDED);
  }

  /**
   * Compone la clave a la que se aplica el cupo.
   * En las rutas de autenticación es la dirección de origen a secas, porque aún no hay
   * ninguna identidad y lo que se quiere frenar es al que insiste desde un sitio. En el
   * resto se añade la credencial presentada, de modo que varios usuarios detrás de una
   * misma salida a internet no se consuman el cupo entre ellos.
   *
   * <p>La credencial no se descifra ni se valida aquí: solo se usa su huella para separar
   * clientes. Se resume con SHA-256 y no con el código de dispersión de la cadena, que es
   * de 32 bits y permitiría fabricar una cabecera que cayera en el mismo cupo que la de
   * otro cliente para agotárselo.</p>
   *
   * @param exchange intercambio HTTP en curso
   * @param auth     si la petición va a una ruta de autenticación
   * @return la clave del cupo
   */
  private String key(ServerWebExchange exchange, boolean auth) {
    String client = clientAddress(exchange);
    if (auth) {
      return client;
    }
    String credential = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    return credential == null ? client : client + '|' + fingerprint(credential);
  }

  /**
   * Resume la credencial presentada en una huella corta con la que separar clientes.
   * Solo se guardan los primeros bytes del resumen: distinguen de sobra a los clientes de
   * una misma dirección y mantienen pequeña la clave del mapa de cupos.
   *
   * @param credential valor de la cabecera de autorización
   * @return la huella en hexadecimal
   */
  private String fingerprint(String credential) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(credential.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, FINGERPRINT_BYTES);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required to separate rate limit buckets", ex);
    }
  }

  /**
   * Obtiene la dirección de origen de la petición.
   * Se toma de la conexión y no de una cabecera de la petición: {@code X-Forwarded-For} lo
   * escribe cualquiera, y confiar en ella permitiría saltarse el cupo cambiando de valor en
   * cada intento. Tras un proxy inverso es este quien debe reescribir la dirección, algo
   * que Spring resuelve con {@code server.forward-headers-strategy}.
   *
   * @param exchange intercambio HTTP en curso
   * @return la dirección de origen, o un valor común si no puede determinarse
   */
  private String clientAddress(ServerWebExchange exchange) {
    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
    return remote == null || remote.getAddress() == null
        ? "unknown"
        : remote.getAddress().getHostAddress();
  }
}
