package com.sreyes.finscope.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.exception.custom.PushNotConfiguredException;
import com.sreyes.finscope.model.query.NotificationSettings;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.PushService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Pruebas del contrato HTTP expuesto por {@link PushController}.
 */
@WebFluxTest(PushController.class)
@Import(TimeConfig.class)
class PushControllerTest {

  private static final Long USER_ID = 7L;
  private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";
  private static final String P256DH =
      "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
  private static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";

  @Autowired
  private WebTestClient webTestClient;

  @MockitoBean
  private PushService pushService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutateWith(mockUser()).mutateWith(csrf());
    when(authenticatedUser.currentUserId()).thenReturn(Mono.just(USER_ID));
  }

  @Test
  @DisplayName("Publica la clave pública solo si los avisos están configurados")
  void exposesConfig() {
    when(pushService.enabled()).thenReturn(true);
    when(pushService.publicKey()).thenReturn("BPublica");

    webTestClient.get().uri("/push/config").exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.enabled").isEqualTo(true)
        .jsonPath("$.publicKey").isEqualTo("BPublica");

    when(pushService.enabled()).thenReturn(false);

    webTestClient.get().uri("/push/config").exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.enabled").isEqualTo(false)
        .jsonPath("$.publicKey").doesNotExist();
  }

  @Test
  @DisplayName("Registra el dispositivo con la suscripción que entrega el navegador")
  void savesSubscription() {
    when(pushService.subscribe(USER_ID, ENDPOINT, P256DH, AUTH)).thenReturn(Mono.empty());

    webTestClient.post().uri("/push/subscriptions")
        .bodyValue(Map.of("endpoint", ENDPOINT, "keys", Map.of("p256dh", P256DH, "auth", AUTH)))
        .exchange()
        .expectStatus().isNoContent();

    verify(pushService).subscribe(USER_ID, ENDPOINT, P256DH, AUTH);
  }

  @Test
  @DisplayName("Rechaza claves del navegador con forma imposible antes de llegar al servicio")
  void rejectsMalformedKeys() {
    webTestClient.post().uri("/push/subscriptions")
        .bodyValue(Map.of("endpoint", ENDPOINT, "keys", Map.of("p256dh", "corta", "auth", AUTH)))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Traduce a 503 los avisos sin configurar")
  void reportsNotConfigured() {
    when(pushService.sendTest(USER_ID)).thenReturn(Mono.error(
        new PushNotConfiguredException("Push notifications are not configured")));

    webTestClient.post().uri("/push/test").exchange()
        .expectStatus().isEqualTo(503)
        .expectBody()
        .jsonPath("$.code").isEqualTo("PUSH_NOT_CONFIGURED");
  }

  @Test
  @DisplayName("Da de baja el dispositivo por su dirección")
  void deletesSubscription() {
    when(pushService.unsubscribe(USER_ID, ENDPOINT)).thenReturn(Mono.empty());

    webTestClient.delete()
        .uri(builder -> builder.path("/push/subscriptions").queryParam("endpoint", ENDPOINT)
            .build())
        .exchange()
        .expectStatus().isNoContent();

    verify(pushService).unsubscribe(USER_ID, ENDPOINT);
  }

  @Test
  @DisplayName("Cambia solo la preferencia que llega")
  void updatesPreferences() {
    when(pushService.updateSettings(USER_ID, null, false))
        .thenReturn(Mono.just(new NotificationSettings(true, false)));

    webTestClient.patch().uri("/push/preferences")
        .bodyValue(Map.of("budgetLimit", false))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.recurringDue").isEqualTo(true)
        .jsonPath("$.budgetLimit").isEqualTo(false);
  }

  @Test
  @DisplayName("Dice a cuántos dispositivos llegó el aviso de prueba")
  void sendsTest() {
    when(pushService.sendTest(USER_ID)).thenReturn(Mono.just(2));

    webTestClient.post().uri("/push/test").exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.delivered").isEqualTo(2);
  }
}
