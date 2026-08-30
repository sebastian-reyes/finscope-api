package com.sreyes.finscope.security;

import static org.mockito.Mockito.when;

import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.controller.TransactionTypeController;
import com.sreyes.finscope.service.TransactionTypeService;
import com.sreyes.finscope.util.mapper.TransactionTypeMapperImpl;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

/**
 * Pruebas de la cadena de seguridad: qué tokens se aceptan, qué orígenes pueden consumir la
 * API desde un navegador y qué cabeceras de protección salen en cada respuesta.
 * Se levanta la configuración real en lugar de simular la autenticación, porque lo que se
 * comprueba aquí es precisamente lo que decide esa configuración.
 */
@WebFluxTest(TransactionTypeController.class)
@Import({SecurityConfig.class, TimeConfig.class, ErrorResponseWriter.class,
    RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
    TransactionTypeMapperImpl.class})
@TestPropertySource(properties = {
    "finscope.security.jwt.secret=clave-de-firma-de-pruebas-de-mas-de-32-bytes",
    "finscope.security.jwt.issuer=finscope-api",
    "finscope.security.jwt.audience=finscope-web",
    "finscope.security.jwt.access-token-ttl=15m",
    "finscope.security.jwt.refresh-token-ttl=30d",
    "finscope.security.cors.allowed-origins=https://app.finscope.test",
    "finscope.security.rate-limit.enabled=false"
})
class SecurityConfigTest {

  private static final String PROTECTED_PATH = "/transaction-types";
  private static final String ALLOWED_ORIGIN = "https://app.finscope.test";
  private static final String FOREIGN_ORIGIN = "https://atacante.test";

  @Autowired
  private WebTestClient webTestClient;

  @Autowired
  private JwtEncoder jwtEncoder;

  @MockitoBean
  private TransactionTypeService transactionTypeService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutate().baseUrl("http://localhost:9090").build();
    when(transactionTypeService.findAllTransactionTypes()).thenReturn(Flux.empty());
  }

  @Test
  @DisplayName("Rechaza una petición sin token")
  void rejectsRequestWithoutToken() {
    webTestClient.get().uri(PROTECTED_PATH)
        .exchange()
        .expectStatus().isUnauthorized()
        .expectBody()
        .jsonPath("$.code").isEqualTo("UNAUTHENTICATED");
  }

  @Test
  @DisplayName("Rechaza un token manipulado")
  void rejectsTamperedToken() {
    String token = token("finscope-api", "finscope-web", "7", Instant.now().plusSeconds(900));
    webTestClient.get().uri(PROTECTED_PATH)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("Rechaza un token caducado")
  void rejectsExpiredToken() {
    String token = token("finscope-api", "finscope-web", "7",
        Instant.now().minus(1, ChronoUnit.HOURS));
    webTestClient.get().uri(PROTECTED_PATH)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("Rechaza un token emitido por otro emisor")
  void rejectsTokenFromAnotherIssuer() {
    String token = token("otro-emisor", "finscope-web", "7", Instant.now().plusSeconds(900));
    webTestClient.get().uri(PROTECTED_PATH)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("Rechaza un token dirigido a otro destinatario")
  void rejectsTokenForAnotherAudience() {
    String token = token("finscope-api", "otra-aplicacion", "7", Instant.now().plusSeconds(900));
    webTestClient.get().uri(PROTECTED_PATH)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("Rechaza un token sin sujeto")
  void rejectsTokenWithoutSubject() {
    String token = token("finscope-api", "finscope-web", null, Instant.now().plusSeconds(900));
    webTestClient.get().uri(PROTECTED_PATH)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  @DisplayName("Acepta un token válido y añade las cabeceras de protección")
  void acceptsValidTokenAndAddsSecurityHeaders() {
    String token = token("finscope-api", "finscope-web", "7", Instant.now().plusSeconds(900));
    webTestClient.get().uri(PROTECTED_PATH)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
        .expectHeader().valueEquals("X-Frame-Options", "DENY")
        .expectHeader().valueEquals("Referrer-Policy", "no-referrer")
        .expectHeader().exists("Content-Security-Policy")
        .expectHeader().exists("Permissions-Policy");
  }

  @Test
  @DisplayName("Permite el preflight del origen autorizado")
  void allowsConfiguredOrigin() {
    webTestClient.options().uri(PROTECTED_PATH)
        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN);
  }

  @Test
  @DisplayName("Rechaza el preflight de un origen no autorizado")
  void rejectsForeignOrigin() {
    webTestClient.options().uri(PROTECTED_PATH)
        .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
        .exchange()
        .expectStatus().isForbidden()
        .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }

  /**
   * Emite un token firmado con la clave real de la aplicación y los datos indicados.
   * Se firma de verdad para que lo que se pruebe sea la validación de las declaraciones y
   * no la de la firma, que ya se cubre con el token manipulado.
   *
   * @param issuer    emisor a registrar
   * @param audience  destinatario a registrar
   * @param subject   sujeto a registrar, o nulo para omitirlo
   * @param expiresAt caducidad del token
   * @return el token firmado
   */
  private String token(String issuer, String audience, String subject, Instant expiresAt) {
    JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .audience(List.of(audience))
        .issuedAt(expiresAt.minusSeconds(60))
        .expiresAt(expiresAt);
    if (subject != null) {
      claims.subject(subject);
    }
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
  }
}
