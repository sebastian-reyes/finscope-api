package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.PushEndpointNotAllowedException;
import com.sreyes.finscope.exception.custom.PushNotConfiguredException;
import com.sreyes.finscope.model.query.NotificationSettings;
import com.sreyes.finscope.model.query.PushMessage;
import com.sreyes.finscope.repository.NotificationRepository;
import com.sreyes.finscope.repository.PushSubscriptionRepository;
import com.sreyes.finscope.security.VapidKeys;
import com.sreyes.finscope.service.PushService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.rules.PushEndpointRules;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link PushService}.
 *
 * <p>Las suscripciones que el servicio de push da por muertas se borran en el mismo envío.
 * Es la única forma de enterarse: un teléfono que quita el permiso o desinstala la aplicación
 * no avisa a nadie, simplemente su dirección empieza a responder 404 o 410.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushServiceImpl implements PushService {

  private final PushSubscriptionRepository subscriptionRepository;
  private final NotificationRepository notificationRepository;
  private final WebPushGateway gateway;
  private final VapidKeys vapidKeys;
  private final Clock clock;

  @Override
  public boolean enabled() {
    return vapidKeys.enabled();
  }

  @Override
  public String publicKey() {
    return vapidKeys.publicKey();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Sin claves no se guarda nada: una suscripción creada contra otra clave pública —o
   * contra ninguna— no serviría el día que se configuren, y el navegador tendría que volver a
   * suscribirse igualmente.</p>
   */
  @Override
  public Mono<Void> subscribe(Long userId, String endpoint, String p256dh, String auth) {
    return Mono.defer(() -> {
      requireEnabled();
      if (!PushEndpointRules.isAllowed(endpoint)) {
        return Mono.error(new PushEndpointNotAllowedException(
            Constants.PUSH_ENDPOINT_NOT_ALLOWED));
      }
      return subscriptionRepository.upsert(userId, endpoint.strip(), p256dh.strip(),
          auth.strip()).then();
    });
  }

  @Override
  public Mono<Void> unsubscribe(Long userId, String endpoint) {
    return subscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint.strip()).then();
  }

  @Override
  public Mono<NotificationSettings> findSettings(Long userId) {
    return notificationRepository.findSettings(userId);
  }

  @Override
  public Mono<NotificationSettings> updateSettings(Long userId, Boolean recurringDue,
                                                   Boolean budgetLimit) {
    return notificationRepository.saveSettings(userId, recurringDue, budgetLimit);
  }

  @Override
  public Mono<Integer> sendTest(Long userId) {
    return Mono.defer(() -> {
      requireEnabled();
      return notifyUser(userId, new PushMessage(
          "Avisos activados",
          "Así te avisará FinScope cuando venza un fijo o un presupuesto llegue a su límite.",
          "/account",
          "finscope-test"))
          .map(Delivery::delivered);
    });
  }

  /**
   * {@inheritDoc}
   *
   * <p>Los dispositivos se recorren uno detrás de otro y no en paralelo: son dos o tres por
   * usuario, y así un servicio lento no multiplica las conexiones abiertas a la vez.</p>
   */
  @Override
  public Mono<Delivery> notifyUser(Long userId, PushMessage message) {
    return subscriptionRepository.findByUserId(userId)
        .concatMap(subscription -> gateway.send(subscription, message)
            .flatMap(outcome -> switch (outcome) {
              case DELIVERED -> subscriptionRepository
                  .markSucceeded(subscription.getId(), LocalDateTime.now(clock))
                  .thenReturn(outcome);
              case GONE -> subscriptionRepository.delete(subscription)
                  .doOnSuccess(done -> log.info("Removed expired push subscription {}",
                      subscription.getId()))
                  .thenReturn(outcome);
              default -> Mono.just(outcome);
            }))
        .reduce(new Delivery(0, 0), (total, outcome) -> switch (outcome) {
          case DELIVERED -> new Delivery(total.delivered() + 1, total.retryable());
          case RETRYABLE -> new Delivery(total.delivered(), total.retryable() + 1);
          default -> total;
        });
  }

  private void requireEnabled() {
    if (!vapidKeys.enabled()) {
      throw new PushNotConfiguredException(Constants.PUSH_NOT_CONFIGURED);
    }
  }
}
