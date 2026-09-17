package com.sreyes.finscope.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.exception.custom.PushEndpointNotAllowedException;
import com.sreyes.finscope.exception.custom.PushNotConfiguredException;
import com.sreyes.finscope.model.entity.PushSubscription;
import com.sreyes.finscope.model.query.PushMessage;
import com.sreyes.finscope.repository.NotificationRepository;
import com.sreyes.finscope.repository.PushSubscriptionRepository;
import com.sreyes.finscope.security.VapidKeys;
import com.sreyes.finscope.service.PushService.Delivery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas de {@link PushServiceImpl}: qué suscripciones se aceptan y qué se hace con cada
 * respuesta del servicio de push.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc";
  private static final PushMessage MESSAGE = new PushMessage("t", "b", "/", "tag");

  @Mock
  private PushSubscriptionRepository subscriptionRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private WebPushGateway gateway;

  @Mock
  private VapidKeys vapidKeys;

  private PushServiceImpl service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneOffset.UTC);
    service = new PushServiceImpl(subscriptionRepository, notificationRepository, gateway,
        vapidKeys, clock);
    when(vapidKeys.enabled()).thenReturn(true);
  }

  private PushSubscription subscription(long id) {
    return new PushSubscription(id, USER_ID, ENDPOINT + id, "p", "a", null, null);
  }

  @Test
  @DisplayName("Guarda la suscripción de un servicio de push conocido")
  void savesKnownEndpoint() {
    when(subscriptionRepository.upsert(USER_ID, ENDPOINT, "p256", "auth"))
        .thenReturn(Mono.just(1L));

    StepVerifier.create(service.subscribe(USER_ID, " " + ENDPOINT + " ", "p256", "auth"))
        .verifyComplete();

    verify(subscriptionRepository).upsert(USER_ID, ENDPOINT, "p256", "auth");
  }

  @Test
  @DisplayName("Rechaza una dirección que no es de un servicio de push")
  void rejectsUnknownEndpoint() {
    StepVerifier.create(service.subscribe(USER_ID, "https://10.0.0.5/admin", "p", "a"))
        .expectError(PushEndpointNotAllowedException.class)
        .verify();

    verify(subscriptionRepository, never()).upsert(anyLong(), anyString(), anyString(),
        anyString());
  }

  @Test
  @DisplayName("Sin claves no guarda suscripciones ni manda pruebas")
  void failsWhenNotConfigured() {
    when(vapidKeys.enabled()).thenReturn(false);

    StepVerifier.create(service.subscribe(USER_ID, ENDPOINT, "p", "a"))
        .expectError(PushNotConfiguredException.class)
        .verify();
    StepVerifier.create(service.sendTest(USER_ID))
        .expectError(PushNotConfiguredException.class)
        .verify();
  }

  @Test
  @DisplayName("Cuenta los entregados, borra los muertos y separa lo que puede reintentarse")
  void handlesEachOutcome() {
    PushSubscription phone = subscription(1);
    PushSubscription laptop = subscription(2);
    PushSubscription tablet = subscription(3);
    when(subscriptionRepository.findByUserId(USER_ID))
        .thenReturn(Flux.just(phone, laptop, tablet));
    when(gateway.send(phone, MESSAGE)).thenReturn(Mono.just(WebPushGateway.Outcome.DELIVERED));
    when(gateway.send(laptop, MESSAGE)).thenReturn(Mono.just(WebPushGateway.Outcome.GONE));
    when(gateway.send(tablet, MESSAGE)).thenReturn(Mono.just(WebPushGateway.Outcome.RETRYABLE));
    when(subscriptionRepository.markSucceeded(eq(1L), any())).thenReturn(Mono.just(1L));
    when(subscriptionRepository.delete(laptop)).thenReturn(Mono.empty());

    StepVerifier.create(service.notifyUser(USER_ID, MESSAGE))
        .expectNext(new Delivery(1, 1))
        .verifyComplete();

    verify(subscriptionRepository).delete(laptop);
    verify(subscriptionRepository, never()).delete(tablet);
  }

  @Test
  @DisplayName("Un usuario sin dispositivos recibe cero entregas, no un error")
  void userWithoutDevicesGetsZero() {
    when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(Flux.empty());

    StepVerifier.create(service.sendTest(USER_ID))
        .expectNext(0)
        .verifyComplete();
  }
}
