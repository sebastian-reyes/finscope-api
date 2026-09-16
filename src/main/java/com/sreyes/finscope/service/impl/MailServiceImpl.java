package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.security.MailProperties;
import com.sreyes.finscope.service.MailService;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Implementación del servicio {@link MailService}.
 * Manda los correos por SMTP con el remitente configurado, y compone cada uno en las dos
 * formas —texto y HTML— que admite un mensaje: el cliente de correo elige, y el texto es
 * además lo que se lee cuando el HTML no se pinta.
 *
 * <p>El envío es bloqueante: JavaMail abre un socket y espera. Va por eso en
 * {@code Schedulers.boundedElastic()}, igual que el cálculo de un hash de contraseña, para
 * que un servidor de correo lento no se lleve por delante el bucle de eventos que atiende al
 * resto de peticiones.</p>
 *
 * <p>Sin {@code spring.mail.host} configurado no hay transporte: Spring no crea el emisor y
 * el correo se escribe en el registro en lugar de salir. Es a propósito, y es lo que permite
 * desarrollar el flujo entero —pulsando el enlace que aparece en la consola— sin dar de alta
 * ningún proveedor.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {

  /**
   * Rutas de la aplicación web a las que llevan los enlaces de cada correo.
   */
  private static final String VERIFY_PATH = "/verify-email";
  private static final String RESET_PATH = "/reset-password";
  private static final String CHANGE_PATH = "/change-email";

  /**
   * Emisor de correo, presente solo si el entorno configura un servidor SMTP.
   * Se pide como proveedor y no como dependencia directa para que la aplicación arranque sin
   * él: el resto de FinScope funciona igual, y exigir un servidor de correo para poder
   * registrar un gasto sería un requisito desproporcionado.
   */
  private final ObjectProvider<JavaMailSender> mailSender;
  private final MailProperties properties;

  @Override
  public Mono<Void> sendEmailVerification(String to, String displayName, String token) {
    String link = properties.link(VERIFY_PATH, token);
    return send(to, "Confirma tu correo en FinScope", body(
        greeting(displayName),
        "Confirma que esta dirección es tuya para que FinScope pueda escribirte si alguna "
            + "vez necesitas recuperar tu contraseña.",
        "Confirmar mi correo",
        link,
        "El enlace vale " + describe(properties.verificationTtl())
            + ". Si no has creado ninguna cuenta en FinScope, puedes ignorar este correo."));
  }

  @Override
  public Mono<Void> sendPasswordReset(String to, String displayName, String token) {
    String link = properties.link(RESET_PATH, token);
    return send(to, "Restablece tu contraseña de FinScope", body(
        greeting(displayName),
        "Has pedido establecer una contraseña nueva. Al hacerlo se cerrarán todas las "
            + "sesiones que tengas abiertas.",
        "Elegir contraseña nueva",
        link,
        "El enlace vale " + describe(properties.passwordResetTtl())
            + " y solo puede usarse una vez. Si no has pedido nada, tu contraseña sigue "
            + "siendo la de siempre y no tienes que hacer nada."));
  }

  @Override
  public Mono<Void> sendEmailChange(String to, String displayName, String token) {
    String link = properties.link(CHANGE_PATH, token);
    return send(to, "Confirma tu correo nuevo en FinScope", body(
        greeting(displayName),
        "Has pedido mover tu cuenta de FinScope a esta dirección. Hasta que lo confirmes, "
            + "tu cuenta sigue entrando con la anterior.",
        "Usar esta dirección",
        link,
        "El enlace vale " + describe(properties.emailChangeTtl())
            + ". Si no has pedido nada, ignora este correo: sin este paso no cambia nada."));
  }

  @Override
  public Mono<Void> sendEmailChangeNotice(String to, String displayName, String newEmail) {
    return send(to, "Se ha pedido cambiar el correo de tu cuenta de FinScope", body(
        greeting(displayName),
        "Se ha pedido mover tu cuenta de FinScope a <strong>" + escape(newEmail)
            + "</strong>. El cambio no se ha aplicado todavía: solo ocurrirá si se confirma "
            + "desde esa dirección.",
        null,
        null,
        "Si no has sido tú, cambia tu contraseña cuanto antes: quien lo haya pedido tiene "
            + "acceso a tu sesión."));
  }

  /**
   * Manda un mensaje al destinatario indicado.
   * Si no hay transporte configurado el correo no se pierde en silencio: se registra entero,
   * con el enlace incluido, que es lo que hace falta para seguir el flujo en desarrollo.
   *
   * @param to      destinatario
   * @param subject asunto del mensaje
   * @param html    cuerpo del mensaje en HTML
   * @return Mono vacío al completar el envío
   */
  private Mono<Void> send(String to, String subject, String html) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.warn("No SMTP server configured (spring.mail.host), so this message stays here."
          + "\nTo: {}\nSubject: {}\n{}", to, subject, text(html));
      return Mono.empty();
    }
    return Mono.fromRunnable(() -> {
      try {
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(properties.from());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(text(html), html);
        sender.send(message);
        log.info("Sent account email to {}", to);
      } catch (Exception ex) {
        throw new IllegalStateException("Could not send the account email", ex);
      }
    }).subscribeOn(Schedulers.boundedElastic()).then();
  }

  /**
   * Compone el cuerpo del mensaje con la misma forma en los cuatro correos.
   * Los estilos van en línea porque es lo único que respetan los clientes de correo, y la
   * dirección del enlace se repite debajo del botón para quien no pueda pulsarlo.
   *
   * @param greeting   saludo, con el nombre si lo hay
   * @param message    de qué va el correo, en HTML
   * @param buttonText texto del botón, o nulo si el correo no lleva ninguno
   * @param link       dirección a la que lleva el botón, o nulo
   * @param footer     advertencia final
   * @return el cuerpo del mensaje en HTML
   */
  private String body(String greeting, String message, String buttonText, String link,
                      String footer) {
    String action = link == null ? "" : ("<p style=\"margin:0 0 24px\"><a href=\"" + link
        + "\" style=\"display:inline-block;padding:12px 20px;border-radius:10px;"
        + "background:#2563eb;color:#ffffff;font-weight:600;text-decoration:none\">"
        + buttonText + "</a></p>"
        + "<p style=\"margin:0 0 24px;font-size:13px;color:#64748b;word-break:break-all\">"
        + "O copia esta dirección en tu navegador:<br>" + link + "</p>");
    return "<div style=\"font-family:system-ui,-apple-system,'Segoe UI',sans-serif;"
        + "font-size:15px;line-height:1.6;color:#0f172a;max-width:520px;margin:0 auto;"
        + "padding:24px\">"
        + "<p style=\"margin:0 0 8px;font-size:20px;font-weight:700\">FinScope</p>"
        + "<p style=\"margin:0 0 16px\">" + greeting + "</p>"
        + "<p style=\"margin:0 0 24px\">" + message + "</p>"
        + action
        + "<p style=\"margin:0;font-size:13px;color:#64748b;border-top:1px solid #e2e8f0;"
        + "padding-top:16px\">" + footer + "</p>"
        + "</div>";
  }

  /**
   * Saluda por el nombre si la cuenta tiene uno puesto.
   *
   * @param displayName nombre del usuario, que puede no existir
   * @return el saludo
   */
  private String greeting(String displayName) {
    return displayName == null || displayName.isBlank()
        ? "Hola:"
        : "Hola, " + escape(displayName) + ":";
  }

  /**
   * Deriva la versión en texto plano a partir del HTML.
   * Se genera en lugar de escribirse aparte para que las dos versiones no puedan decir cosas
   * distintas: cualquier cambio en el mensaje llega a las dos a la vez.
   *
   * @param html cuerpo del mensaje en HTML
   * @return el mismo mensaje sin etiquetas
   */
  private String text(String html) {
    return html
        .replaceAll("(?i)<br\\s*/?>", "\n")
        .replaceAll("(?i)</p>", "\n\n")
        .replaceAll("<[^>]+>", "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replaceAll("\n{3,}", "\n\n")
        .strip();
  }

  /**
   * Escapa lo que escribe el usuario antes de meterlo en el HTML del correo.
   * El nombre y la dirección nueva los elige una persona, así que sin esto un nombre con
   * etiquetas dentro acabaría interpretándose en el cliente de correo de otra.
   *
   * @param value texto a escapar
   * @return el texto listo para insertarse en el HTML
   */
  private String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /**
   * Dice cuánto dura un enlace en las unidades en las que lo diría una persona.
   *
   * @param ttl validez del enlace
   * @return la duración en palabras
   */
  private String describe(Duration ttl) {
    if (ttl.toHours() >= 24 && ttl.toMinutes() % (24 * 60) == 0) {
      long days = ttl.toDays();
      return days == 1 ? "un día" : days + " días";
    }
    if (ttl.toHours() >= 1 && ttl.toMinutes() % 60 == 0) {
      long hours = ttl.toHours();
      return hours == 1 ? "una hora" : hours + " horas";
    }
    long minutes = Math.max(1, ttl.toMinutes());
    return minutes == 1 ? "un minuto" : minutes + " minutos";
  }
}
