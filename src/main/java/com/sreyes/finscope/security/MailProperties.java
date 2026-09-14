package com.sreyes.finscope.security;

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
   */
  public MailProperties {
    if (from == null || from.isBlank()) {
      throw new IllegalStateException("finscope.mail.from must be configured");
    }
    if (appBaseUrl == null || appBaseUrl.isBlank()) {
      throw new IllegalStateException("finscope.mail.app-base-url must be configured");
    }
    appBaseUrl = appBaseUrl.strip();
    while (appBaseUrl.endsWith("/")) {
      appBaseUrl = appBaseUrl.substring(0, appBaseUrl.length() - 1);
    }
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
  private static Duration positive(Duration value, Duration fallback) {
    return value == null || value.isZero() || value.isNegative() ? fallback : value;
  }
}
