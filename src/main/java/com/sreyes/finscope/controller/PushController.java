package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.PushApi;
import com.sreyes.finscope.api.model.NotificationPreferencesResponse;
import com.sreyes.finscope.api.model.PushConfigResponse;
import com.sreyes.finscope.api.model.PushTestResponse;
import com.sreyes.finscope.api.model.SavePushSubscriptionRequest;
import com.sreyes.finscope.api.model.UpdateNotificationPreferencesRequest;
import com.sreyes.finscope.model.query.NotificationSettings;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.PushService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Controlador REST de los avisos al teléfono.
 * Implementa el contrato {@link PushApi} generado a partir de la especificación OpenAPI.
 *
 * Aquí solo se gestiona el lado del usuario: sus dispositivos, sus preferencias y el aviso de
 * prueba. Los avisos de verdad no los pide nadie; los manda la tarea programada de
 * {@link com.sreyes.finscope.service.NotificationService}.
 */
@RestController
@RequiredArgsConstructor
public class PushController implements PushApi {

  private final PushService pushService;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<PushConfigResponse>> getPushConfig(ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .map(userId -> {
          PushConfigResponse response = new PushConfigResponse(pushService.enabled());
          if (pushService.enabled()) {
            response.setPublicKey(pushService.publicKey());
          }
          return ResponseEntity.ok(response);
        });
  }

  @Override
  public Mono<ResponseEntity<Void>> savePushSubscription(
      Mono<SavePushSubscriptionRequest> request, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(request)
        .flatMap(tuple -> pushService.subscribe(tuple.getT1(), tuple.getT2().getEndpoint(),
            tuple.getT2().getKeys().getP256dh(), tuple.getT2().getKeys().getAuth()))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<Void>> deletePushSubscription(String endpoint,
                                                           ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> pushService.unsubscribe(userId, endpoint))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<NotificationPreferencesResponse>> getNotificationPreferences(
      ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(pushService::findSettings)
        .map(settings -> ResponseEntity.ok(toResponse(settings)));
  }

  @Override
  public Mono<ResponseEntity<NotificationPreferencesResponse>> updateNotificationPreferences(
      Mono<UpdateNotificationPreferencesRequest> request, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(request)
        .flatMap(tuple -> pushService.updateSettings(tuple.getT1(),
            tuple.getT2().getRecurringDue(), tuple.getT2().getBudgetLimit()))
        .map(settings -> ResponseEntity.ok(toResponse(settings)));
  }

  @Override
  public Mono<ResponseEntity<PushTestResponse>> sendTestNotification(
      ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(pushService::sendTest)
        .map(delivered -> ResponseEntity.ok(new PushTestResponse(delivered)));
  }

  private static NotificationPreferencesResponse toResponse(NotificationSettings settings) {
    return new NotificationPreferencesResponse(settings.recurringDue(), settings.budgetLimit());
  }
}
