package com.sreyes.finscope.security;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de los avisos al teléfono (Web Push).
 *
 * <p>Las claves VAPID son la identidad del servidor ante los servicios de push: la pública
 * viaja al navegador cuando se suscribe y la privada firma cada envío. Se generan una sola
 * vez y no se cambian, porque cambiarlas deja inservibles todas las suscripciones que ya
 * existen: cada una quedó atada a la clave pública con la que se creó.</p>
 *
 * <p>Sin claves la aplicación arranca igual y los avisos quedan apagados, igual que el correo
 * sin servidor SMTP: el resto de FinScope no depende de ellos. Lo que no se admite es la
 * mitad —una clave sin la otra— ni un marcador sin resolver, que es lo que Spring enlaza
 * cuando falta la variable y que pasaría por una clave hasta el primer envío.</p>
 *
 * @param publicKey       clave pública VAPID, P-256 sin comprimir en Base64 URL
 * @param privateKey      clave privada VAPID, el escalar de 32 bytes en Base64 URL
 * @param subject         contacto del remitente para los servicios de push, {@code mailto:} o
 *                        {@code https:}
 * @param zone            zona horaria en la que se decide qué es «mañana» y a qué hora avisar
 * @param windowStartHour primera hora del día en la que se manda un aviso programado
 * @param windowEndHour   hora a partir de la cual ya no se mandan hasta el día siguiente
 * @param budgetNearRatio fracción del presupuesto a partir de la cual se avisa de que se acerca
 * @param ttl             cuánto guarda el servicio de push un aviso con el teléfono apagado
 */
@ConfigurationProperties(prefix = "finscope.push")
public record PushProperties(
    String publicKey,
    String privateKey,
    String subject,
    String zone,
    Integer windowStartHour,
    Integer windowEndHour,
    Double budgetNearRatio,
    Duration ttl) {

  private static final String DEFAULT_SUBJECT = "mailto:no-reply@fin-scope.app";
  private static final String DEFAULT_ZONE = "America/Lima";

  /**
   * Aplica los valores por defecto y rechaza las configuraciones a medias.
   *
   * <p>La ventana por defecto va de las 8 a las 21: un recordatorio de pago a las tres de la
   * madrugada no ayuda a nadie a pagar, y la tarea corre cada hora, así que lo que no cabe
   * hoy sale a primera hora de mañana.</p>
   */
  public PushProperties {
    publicKey = blankToNull("finscope.push.public-key", "VAPID_PUBLIC_KEY", publicKey);
    privateKey = blankToNull("finscope.push.private-key", "VAPID_PRIVATE_KEY", privateKey);
    if ((publicKey == null) != (privateKey == null)) {
      throw new IllegalStateException("finscope.push.public-key and finscope.push.private-key "
          + "must be configured together (VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY)");
    }
    subject = blankToNull("finscope.push.subject", "VAPID_SUBJECT", subject);
    subject = subject == null ? DEFAULT_SUBJECT : subject;
    if (!subject.startsWith("mailto:") && !subject.startsWith("https://")) {
      throw new IllegalStateException(
          "finscope.push.subject must start with mailto: or https://: " + subject);
    }
    zone = blankToNull("finscope.push.zone", "PUSH_ZONE", zone);
    zone = zone == null ? DEFAULT_ZONE : zone;
    try {
      ZoneId.of(zone);
    } catch (DateTimeException ex) {
      throw new IllegalStateException("finscope.push.zone is not a valid time zone: " + zone, ex);
    }
    windowStartHour = windowStartHour == null ? 8 : windowStartHour;
    windowEndHour = windowEndHour == null ? 21 : windowEndHour;
    if (windowStartHour < 0 || windowEndHour > 24 || windowStartHour >= windowEndHour) {
      throw new IllegalStateException("finscope.push window must satisfy 0 <= start < end <= 24");
    }
    budgetNearRatio = budgetNearRatio == null ? 0.9 : budgetNearRatio;
    if (budgetNearRatio <= 0 || budgetNearRatio >= 1) {
      throw new IllegalStateException("finscope.push.budget-near-ratio must be between 0 and 1");
    }
    ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofHours(12) : ttl;
  }

  /**
   * Indica si los avisos están configurados.
   *
   * @return si hay claves VAPID con las que firmar
   */
  public boolean enabled() {
    return publicKey != null;
  }

  /**
   * Zona horaria ya interpretada.
   *
   * @return la zona en la que se decide el día y la hora de los avisos
   */
  public ZoneId zoneId() {
    return ZoneId.of(zone);
  }

  /**
   * Quita los espacios y convierte el vacío en nulo, rechazando los marcadores sin resolver.
   *
   * @param property nombre de la propiedad, para el mensaje
   * @param variable variable de entorno de la que suele salir, para el mensaje
   * @param value    valor enlazado
   * @return el valor limpio, o nulo si no hay ninguno
   */
  private static String blankToNull(String property, String variable, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if (value.contains("${")) {
      throw new IllegalStateException(property + " is an unresolved placeholder ("
          + value.strip() + "); define the " + variable + " environment variable");
    }
    return value.strip();
  }
}
