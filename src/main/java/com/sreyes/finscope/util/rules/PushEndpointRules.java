package com.sreyes.finscope.util.rules;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * Qué direcciones se aceptan como suscripción de Web Push.
 * Esta clase no debe ser instanciada.
 *
 * <p>La dirección de una suscripción la manda el cliente, y la API hace después una petición
 * a ella. Sin una lista cerrada eso es una puerta para que cualquiera con cuenta haga que el
 * servidor llame a donde quiera —a un servicio interno de la red de Render, por ejemplo— con
 * solo registrarla y pulsar «probar aviso». Por eso solo valen los servicios de push de los
 * navegadores, que son pocos y conocidos, por HTTPS y en su puerto.</p>
 *
 * <p>Si algún navegador estrena un servicio nuevo, se añade aquí: sus usuarios verán que no se
 * pueden activar los avisos, que es un fallo visible, en lugar de un agujero que no lo es.</p>
 */
@UtilityClass
public final class PushEndpointRules {

  /** Servicios que se aceptan por nombre exacto. */
  private static final List<String> HOSTS = List.of(
      // Chrome, Android, Samsung Internet, Opera y Brave.
      "fcm.googleapis.com",
      // Firefox.
      "updates.push.services.mozilla.com");

  /** Servicios que reparten sus direcciones entre varios subdominios. */
  private static final List<String> HOST_SUFFIXES = List.of(
      // Safari en iPhone, iPad y Mac.
      ".push.apple.com",
      // Edge en Windows.
      ".notify.windows.com");

  /**
   * Decide si una dirección es de un servicio de push conocido.
   *
   * @param endpoint dirección recibida
   * @return si puede guardarse como suscripción
   */
  public static boolean isAllowed(String endpoint) {
    if (endpoint == null || endpoint.isBlank()) {
      return false;
    }
    URI uri;
    try {
      uri = new URI(endpoint.strip());
    } catch (URISyntaxException ex) {
      return false;
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
        || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
      return false;
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    return HOSTS.contains(host) || HOST_SUFFIXES.stream().anyMatch(host::endsWith);
  }
}
