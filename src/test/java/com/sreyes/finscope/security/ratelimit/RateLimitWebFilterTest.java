package com.sreyes.finscope.security.ratelimit;

import com.sreyes.finscope.security.ErrorResponseWriter;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas del filtro de limitación de peticiones. Se monta sobre un endpoint de prueba en
 * lugar de sobre uno real para poder fijar un cupo minúsculo sin depender de la ruta que se
 * esté probando ni de la seguridad, que actúa después.
 */
class RateLimitWebFilterTest {

  private static final String API_PATH = "/probe";
  private static final String AUTH_PATH = "/auth/login";

  private final ErrorResponseWriter errorResponseWriter =
      new ErrorResponseWriter(new ObjectMapper(), Clock.systemUTC());

  @Test
  @DisplayName("Atiende las peticiones que caben en el cupo")
  void allowsRequestsWithinTheLimit() {
    WebTestClient client = clientWith(properties(3, 3));

    for (int attempt = 0; attempt < 3; attempt++) {
      client.get().uri(API_PATH).exchange().expectStatus().isOk();
    }
  }

  @Test
  @DisplayName("Responde 429 con Retry-After al superar el cupo general")
  void rejectsRequestsOverTheApiLimit() {
    WebTestClient client = clientWith(properties(3, 2));

    client.get().uri(API_PATH).exchange().expectStatus().isOk();
    client.get().uri(API_PATH).exchange().expectStatus().isOk();
    client.get().uri(API_PATH).exchange()
        .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectHeader().exists(HttpHeaders.RETRY_AFTER)
        .expectBody()
        .jsonPath("$.code").isEqualTo("RATE_LIMIT_EXCEEDED");
  }

  @Test
  @DisplayName("Aplica a las rutas de autenticación un cupo propio y más estrecho")
  void appliesStricterLimitToAuthRoutes() {
    WebTestClient client = clientWith(properties(1, 50));

    client.post().uri(AUTH_PATH).exchange().expectStatus().isOk();
    client.post().uri(AUTH_PATH).exchange()
        .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    // El cupo de autenticación no consume el general: la API sigue atendiendo.
    client.get().uri(API_PATH).exchange().expectStatus().isOk();
  }

  @Test
  @DisplayName("No limita nada cuando está desactivado")
  void doesNothingWhenDisabled() {
    RateLimitProperties disabled = new RateLimitProperties(false,
        new RateLimitProperties.Bucket(1, Duration.ofMinutes(5)),
        new RateLimitProperties.Bucket(1, Duration.ofMinutes(5)));
    WebTestClient client = clientWith(disabled);

    client.get().uri(API_PATH).exchange().expectStatus().isOk();
    client.get().uri(API_PATH).exchange().expectStatus().isOk();
  }

  /**
   * Compone los límites con las capacidades indicadas y una ventana lo bastante larga como
   * para que no se renueve a mitad de la prueba.
   *
   * @param authCapacity cupo de las rutas de autenticación
   * @param apiCapacity  cupo del resto de la API
   * @return los límites configurados
   */
  private RateLimitProperties properties(int authCapacity, int apiCapacity) {
    return new RateLimitProperties(true,
        new RateLimitProperties.Bucket(authCapacity, Duration.ofMinutes(5)),
        new RateLimitProperties.Bucket(apiCapacity, Duration.ofMinutes(5)));
  }

  /**
   * Monta un cliente con el filtro configurado delante del endpoint de prueba.
   *
   * @param properties límites a aplicar
   * @return el cliente listo para lanzar peticiones
   */
  private WebTestClient clientWith(RateLimitProperties properties) {
    return WebTestClient.bindToController(new TestEndpoint())
        .webFilter(new RateLimitWebFilter(properties, errorResponseWriter))
        .configureClient()
        .baseUrl("http://localhost:9090")
        .build();
  }

  /**
   * Endpoints mínimos sobre los que medir el efecto del filtro: uno bajo la ruta de
   * autenticación y otro fuera de ella, que son los dos cupos que el filtro distingue.
   */
  @RestController
  static class TestEndpoint {

    /**
     * Responde sin hacer nada, para que lo único que decida el resultado sea el filtro.
     *
     * @return respuesta vacía
     */
    @GetMapping(API_PATH)
    Mono<String> probe() {
      return Mono.just("ok");
    }

    /**
     * Equivalente al anterior bajo la ruta de autenticación.
     *
     * @return respuesta vacía
     */
    @PostMapping(AUTH_PATH)
    Mono<String> authProbe() {
      return Mono.just("ok");
    }
  }
}
