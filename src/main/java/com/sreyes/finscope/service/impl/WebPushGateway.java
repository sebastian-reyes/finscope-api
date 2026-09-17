package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.model.entity.PushSubscription;
import com.sreyes.finscope.model.query.PushMessage;
import com.sreyes.finscope.security.PushProperties;
import com.sreyes.finscope.security.VapidKeys;
import com.sreyes.finscope.util.push.WebPushCrypto;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Manda un aviso cifrado al servicio de push de un dispositivo.
 *
 * <p>Va sobre {@link WebClient}, el cliente de WebFlux, y no sobre uno bloqueante: la tarea de
 * avisos puede mandar decenas de mensajes seguidos y no tiene por qué ocupar un hilo por cada
 * uno mientras espera a Google o a Apple.</p>
 *
 * <p>El contenido va en el formato que entiende el trabajador de servicio de Angular: un objeto
 * {@code notification} con lo que se enseña y, dentro, qué hacer al tocarlo. Así el aviso se
 * pinta y abre la pantalla sin que la aplicación web tenga que escribir su propio manejador del
 * evento {@code push}.</p>
 */
@Slf4j
@Component
public class WebPushGateway {

  /** Resultado de un envío, visto desde lo que hay que hacer después. */
  public enum Outcome {
    /** El servicio lo aceptó y lo entregará en cuanto el dispositivo tenga conexión. */
    DELIVERED,
    /** La suscripción ya no existe: el usuario quitó el permiso o desinstaló la aplicación. */
    GONE,
    /** Un fallo pasajero del servicio; vale la pena volver a intentarlo más tarde. */
    RETRYABLE,
    /** El servicio rechazó el mensaje y repetirlo no cambiará nada. */
    REJECTED
  }

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final String ICON = "/icons/icon-192x192.png";
  private static final String BADGE = "/icons/icon-96x96.png";

  private final WebClient webClient;
  private final VapidKeys vapidKeys;
  private final PushProperties properties;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /**
   * Construye el cliente con un tiempo de espera propio: un servicio de push que no contesta
   * no puede dejar la tarea colgada hasta la siguiente hora.
   *
   * @param vapidKeys    claves con las que se firma cada envío
   * @param properties   configuración de los avisos
   * @param objectMapper serializador de la aplicación
   * @param clock        reloj de la aplicación
   */
  public WebPushGateway(VapidKeys vapidKeys, PushProperties properties, ObjectMapper objectMapper,
                        Clock clock) {
    this.webClient = WebClient.builder()
        .clientConnector(new ReactorClientHttpConnector(
            HttpClient.create().responseTimeout(TIMEOUT)))
        .build();
    this.vapidKeys = vapidKeys;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /**
   * Manda un aviso a un dispositivo.
   *
   * @param subscription dispositivo de destino
   * @param message      aviso
   * @return el resultado del envío; nunca termina en error
   */
  public Mono<Outcome> send(PushSubscription subscription, PushMessage message) {
    return Mono.fromCallable(() -> WebPushCrypto.encrypt(payload(message),
            WebPushCrypto.decode(subscription.getP256dh()),
            WebPushCrypto.decode(subscription.getAuth())))
        .flatMap(body -> webClient.post()
            .uri(subscription.getEndpoint())
            .header(HttpHeaders.AUTHORIZATION,
                vapidKeys.authorization(subscription.getEndpoint(), clock.instant()))
            .header(HttpHeaders.CONTENT_ENCODING, "aes128gcm")
            .header("TTL", String.valueOf(properties.ttl().toSeconds()))
            .header("Urgency", "normal")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .bodyValue(body)
            .exchangeToMono(response -> response.releaseBody()
                .thenReturn(outcome(response.statusCode().value(), subscription))))
        .onErrorResume(ex -> {
          log.warn("Push to subscription {} failed: {}", subscription.getId(), ex.toString());
          return Mono.just(ex instanceof IllegalArgumentException
              ? Outcome.GONE
              : Outcome.RETRYABLE);
        });
  }

  /**
   * Interpreta la respuesta del servicio de push.
   * 404 y 410 son las dos formas en que los servicios dicen que la suscripción ya no existe;
   * 429 y 5xx son pasajeros. El resto es un problema del mensaje o de las claves, y se deja
   * en el registro porque no lo va a arreglar reintentar.
   */
  private Outcome outcome(int status, PushSubscription subscription) {
    if (status >= 200 && status < 300) {
      return Outcome.DELIVERED;
    }
    if (status == 404 || status == 410) {
      return Outcome.GONE;
    }
    if (status == 429 || status >= 500) {
      return Outcome.RETRYABLE;
    }
    log.warn("Push service rejected subscription {} with status {}", subscription.getId(),
        status);
    return Outcome.REJECTED;
  }

  private byte[] payload(PushMessage message) {
    Map<String, Object> notification = new LinkedHashMap<>();
    notification.put("title", message.title());
    notification.put("body", message.body());
    notification.put("icon", ICON);
    notification.put("badge", BADGE);
    notification.put("lang", "es");
    notification.put("tag", message.tag());
    notification.put("renotify", true);
    notification.put("data", Map.of("onActionClick", Map.of("default", Map.of(
        "operation", "navigateLastFocusedOrOpen",
        "url", message.url()))));
    return objectMapper.writeValueAsString(Map.of("notification", notification))
        .getBytes(StandardCharsets.UTF_8);
  }
}
