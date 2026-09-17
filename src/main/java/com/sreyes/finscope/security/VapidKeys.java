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

  private static PrivateKey load(PushProperties properties) {
    ECPublicKey publicKey;
    PrivateKey privateKey;
    try {
      publicKey = WebPushCrypto.publicKey(WebPushCrypto.decode(properties.publicKey()));
      privateKey = WebPushCrypto.privateKey(WebPushCrypto.decode(properties.privateKey()));
    } catch (IllegalArgumentException ex) {
      throw new IllegalStateException("VAPID keys are malformed: the public key must be an "
          + "uncompressed P-256 point and the private key a 32-byte scalar, both Base64 URL", ex);
    }
    String probe = "finscope-vapid-check";
    if (!WebPushCrypto.verify(probe, WebPushCrypto.sign(probe, privateKey), publicKey)) {
      throw new IllegalStateException(
          "VAPID_PUBLIC_KEY does not match VAPID_PRIVATE_KEY: they must be the same key pair");
    }
    return privateKey;
  }
}
