package com.sreyes.finscope.security;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de los correos que manda la aplicación y de la validez de sus enlaces.
 * El transporte —servidor, puerto, credenciales— es el estándar de Spring
 * (`spring.mail.*`); aquí solo vive lo que es de FinScope: quién firma los correos, a qué
 * dirección apuntan los enlaces y cuánto duran.
 *
 * <p>Las tres validez son distintas porque protegen cosas distintas. El enlace de
 * restablecer contraseña y el de cambiar de correo son llaves de la cuenta y duran poco. El
 * de verificación no abre nada —solo confirma que la dirección existe— y dura un día, que es
 * lo que tarda una persona en mirar el correo.</p>
 *
 * @param from             remitente de los correos, por ejemplo {@code FinScope <no-reply@...>}
 * @param appBaseUrl       origen de la aplicación web, del que cuelgan los enlaces
 * @param verificationTtl  validez del enlace de verificación del correo
 * @param passwordResetTtl validez del enlace de restablecimiento de contraseña
 * @param emailChangeTtl   validez del enlace de confirmación del correo nuevo
 */
@ConfigurationProperties(prefix = "finscope.mail")
public record MailProperties(
    String from,
    String appBaseUrl,
    Duration verificationTtl,
    Duration passwordResetTtl,
    Duration emailChangeTtl) {

  /**
   * Aplica los valores por defecto y deja la base de los enlaces lista para concatenar.
   * La barra final se quita aquí y no en cada uso: con ella, un valor copiado del navegador
   * —que siempre la trae— produciría enlaces con doble barra que algunos clientes de correo
   * recortan por su cuenta.
   *
   * <p>No basta con comprobar que los valores no estén en blanco. Cuando falta la variable de
   * entorno, Spring no se niega a arrancar: enlaza el marcador tal cual, y
   * {@code finscope.mail.app-base-url} vale literalmente {@code "${APP_BASE_URL}"}. Con esa
   * comprobación sola la aplicación arrancaba y mandaba enlaces a
   * {@code ${APP_BASE_URL}/reset-password}. De ahí que se exija una dirección de verdad y
   * un remitente que se pueda leer como dirección de correo.</p>
   */
  public MailProperties {
    from = requireResolved("finscope.mail.from", "MAIL_FROM", from);
    appBaseUrl = requireResolved("finscope.mail.app-base-url", "APP_BASE_URL", appBaseUrl);
    requireMailbox(from);
    while (appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
    }
    requireWebOrigin(appBaseUrl);
    verificationTtl = positive(verificationTtl, Duration.ofHours(24));
    passwordResetTtl = positive(passwordResetTtl, Duration.ofHours(1));
    emailChangeTtl = positive(emailChangeTtl, Duration.ofHours(1));
  }

  /**
   * Compone el enlace de la aplicación web al que lleva un correo.
   *
   * @param path  ruta dentro de la aplicación, empezando por barra
   * @param token valor del enlace de un solo uso
   * @return la dirección completa a la que apunta el botón del correo
   */
  public String link(String path, String token) {
    return appBaseUrl + path + "?token=" + token;
  }

  /**
   * Devuelve la duración indicada si es utilizable, o la de reserva si no lo es.
   *
   * @param value    duración configurada
   * @param fallback duración a usar cuando no se configura ninguna
   * @return la duración aplicable
   */
  /**
   * Exige un valor presente y que no sea un marcador de variable sin resolver.
   *
   * @param property nombre de la propiedad, para el mensaje
   * @param variable variable de entorno de la que suele salir, para el mensaje
   * @param value    valor enlazado
   * @return el valor sin espacios alrededor
   */
  private static String requireResolved(String property, String variable, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(property + " must be configured");
    }
    if (value.contains("${")) {
      throw new IllegalStateException(property + " is an unresolved placeholder ("
          + value.strip() + "); define the " + variable + " environment variable");
    }
    return value.strip();
  }

  /**
   * Exige un remitente con forma de dirección de correo, con o sin nombre visible.
   *
   * @param from remitente configurado
   */
  private static void requireMailbox(String from) {
    try {
      new InternetAddress(from, true).validate();
    } catch (AddressException ex) {
      throw new IllegalStateException(
          "finscope.mail.from must be a valid email address: " + from, ex);
    }
  }

  /**
   * Exige un origen web absoluto, que es lo único a lo que puede apuntar un enlace de correo.
   *
   * @param appBaseUrl origen de la aplicación, ya sin barra final
   */
  private static void requireWebOrigin(String appBaseUrl) {
    URI uri;
    try {
      uri = new URI(appBaseUrl);
    } catch (URISyntaxException ex) {
      throw new IllegalStateException(
          "finscope.mail.app-base-url must be a valid URL: " + appBaseUrl, ex);
    }
    boolean web = "https".equalsIgnoreCase(uri.getScheme())
        || "http".equalsIgnoreCase(uri.getScheme());
    if (!web || uri.getHost() == null || uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalStateException(
          "finscope.mail.app-base-url must be an absolute http(s) URL without query or "
              + "fragment: " + appBaseUrl);
    }
  }

  private static Duration positive(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }
}
