package com.sreyes.finscope.security;

import com.sreyes.finscope.util.push.WebPushCrypto;
import java.net.URI;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Las claves VAPID ya interpretadas y comprobadas.
 *
 * <p>Se comprueban al arrancar y no al primer envío. Una clave pública que no corresponda a la
 * privada no da ningún error al firmar: el servicio de push rechaza cada mensaje con un 401 y
 * los avisos dejan de llegar sin que nada lo diga. Por eso aquí se firma un texto con la
 * privada y se verifica con la pública, y si no casan la aplicación no arranca.</p>
 */
@Component
public class VapidKeys {

  /**
   * Validez del token VAPID. El estándar admite hasta 24 horas; se usa la mitad para no
   * rozar el límite con la deriva del reloj de los servicios.
   */
  private static final long TOKEN_TTL_SECONDS = 12 * 60 * 60;

  private final PushProperties properties;
  private final PrivateKey privateKey;

  /**
   * Interpreta las claves de la configuración, si las hay.
   *
   * @param properties configuración de los avisos
   */
  public VapidKeys(PushProperties properties) {
    this.properties = properties;
    this.privateKey = properties.enabled() ? load(properties) : null;
  }

  /**
   * Indica si hay claves con las que mandar avisos.
   *
   * @return si los avisos están configurados
   */
  public boolean enabled() {
    return privateKey != null;
  }

  /**
   * Clave pública tal y como la necesita el navegador para suscribirse.
   *
   * @return la clave en Base64 URL, o nulo si los avisos están apagados
   */
  public String publicKey() {
    return properties.publicKey();
  }

  /**
   * Compone la cabecera {@code Authorization} de un envío.
   * La audiencia del token es el origen del servicio de push, no la dirección completa: un
   * token firmado para Google no vale para Apple aunque alguien lo intercepte.
   *
   * @param endpoint dirección de la suscripción
   * @param now      instante del envío
   * @return el valor de la cabecera
   */
  public String authorization(String endpoint, Instant now) {
    if (privateKey == null) {
      throw new IllegalStateException("Push notifications are not configured");
    }
    URI uri = URI.create(endpoint);
    String audience = uri.getScheme() + "://" + uri.getHost()
        + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
    String token = WebPushCrypto.vapidToken(audience, properties.subject(),
        now.getEpochSecond() + TOKEN_TTL_SECONDS, privateKey);
    return "vapid t=" + token + ", k=" + properties.publicKey();
  }

  /**
   * Interpreta y comprueba las dos claves.
   *
   * <p>Los mensajes dicen qué está mal sin enseñar las claves: cuántos caracteres tiene cada
   * una y en cuántos bytes se convierte. Es lo que hace falta para distinguir los tres errores
   * que se cometen al copiarlas en un panel de variables —intercambiarlas, copiar un trozo o
   * pegar la línea entera con el nombre de la variable delante— sin que el secreto acabe en el
   * registro del despliegue.</p>
   */
  private static PrivateKey load(PushProperties properties) {
    byte[] rawPublic = decode("VAPID_PUBLIC_KEY", properties.publicKey());
    byte[] rawPrivate = decode("VAPID_PRIVATE_KEY", properties.privateKey());
    if (rawPublic.length == WebPushCrypto.PRIVATE_KEY_LENGTH
        && rawPrivate.length == WebPushCrypto.PUBLIC_KEY_LENGTH) {
      throw new IllegalStateException("VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY look swapped: the "
          + "public key is the long one (87 characters, starting with B) and the private key "
          + "the short one (43 characters)");
    }
    requireLength("VAPID_PUBLIC_KEY", properties.publicKey(), rawPublic,
        WebPushCrypto.PUBLIC_KEY_LENGTH, "87 characters starting with B");
    requireLength("VAPID_PRIVATE_KEY", properties.privateKey(), rawPrivate,
        WebPushCrypto.PRIVATE_KEY_LENGTH, "43 characters");
    ECPublicKey publicKey;
    PrivateKey privateKey;
    try {
      publicKey = WebPushCrypto.publicKey(rawPublic);
      privateKey = WebPushCrypto.privateKey(rawPrivate);
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("VAPID_PUBLIC_KEY is not a valid P-256 public key; "
          + "generate the pair again with the command in .env.example", ex);
    }
    String probe = "finscope-vapid-check";
    if (!WebPushCrypto.verify(probe, WebPushCrypto.sign(probe, privateKey), publicKey)) {
      throw new IllegalStateException(
          "VAPID_PUBLIC_KEY does not match VAPID_PRIVATE_KEY: they must be the same key pair");
    }
    return privateKey;
  }

  private static byte[] decode(String variable, String value) {
    if (value.startsWith("VAPID_") || value.contains("=B") || value.startsWith("\"")
        || value.startsWith("'")) {
      throw new IllegalStateException(variable + " must contain only the key, without the "
          + "variable name or quotes (" + value.length() + " characters found)");
    }
    try {
      return WebPushCrypto.decode(value);
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException(variable + " is not Base64 URL (" + value.length()
          + " characters found); copy the key again without spaces or line breaks", ex);
    }
  }

  private static void requireLength(String variable, String value, byte[] raw, int expected,
                                    String shape) {
    if (raw.length != expected) {
      throw new IllegalStateException(variable + " has " + value.length() + " characters ("
          + raw.length + " bytes) but must have " + shape + " (" + expected + " bytes); it "
          + "was probably copied incomplete");
    }
  }
}
