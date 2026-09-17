package com.sreyes.finscope.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sreyes.finscope.util.push.WebPushCrypto;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la configuración de los avisos y de la carga de las claves VAPID. Protegen dos
 * fallos que no darían ningún error visible: una clave a medias, y un par de claves que no se
 * corresponden, que haría que los servicios de push rechazaran todos los envíos en silencio.
 */
class PushPropertiesTest {

  @Test
  @DisplayName("Sin claves arranca con los avisos apagados y los valores por defecto")
  void startsDisabledWithoutKeys() {
    PushProperties properties = new PushProperties("", " ", null, null, null, null, null, null);

    assertThat(properties.enabled()).isFalse();
    assertThat(properties.zone()).isEqualTo("America/Lima");
    assertThat(properties.windowStartHour()).isEqualTo(8);
    assertThat(properties.windowEndHour()).isEqualTo(21);
    assertThat(properties.budgetNearRatio()).isEqualTo(0.9);
    assertThat(new VapidKeys(properties).enabled()).isFalse();
  }

  @Test
  @DisplayName("Rechaza una clave sin la otra")
  void rejectsHalfConfiguredKeys() {
    assertThatThrownBy(() -> new PushProperties("BPublica", null, null, null, null, null, null,
        null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("together");
  }

  @Test
  @DisplayName("Rechaza el marcador sin resolver de una variable que falta")
  void rejectsUnresolvedPlaceholder() {
    assertThatThrownBy(() -> new PushProperties("${VAPID_PUBLIC_KEY}", "x", null, null, null,
        null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("VAPID_PUBLIC_KEY");
  }

  @Test
  @DisplayName("Rechaza una zona horaria o una ventana imposibles")
  void rejectsInvalidZoneAndWindow() {
    assertThatThrownBy(() -> new PushProperties(null, null, null, "Lima/Peru", null, null, null,
        null)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new PushProperties(null, null, null, null, 21, 8, null, null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Carga un par de claves válido y firma la cabecera de autorización")
  void loadsMatchingKeyPair() {
    KeyPair pair = WebPushCrypto.generateKeyPair();
    VapidKeys keys = new VapidKeys(properties(pair.getPublic(), pair.getPrivate()));

    String authorization = keys.authorization("https://fcm.googleapis.com/fcm/send/abc",
        Instant.parse("2026-09-17T12:00:00Z"));

    assertThat(keys.enabled()).isTrue();
    assertThat(authorization).startsWith("vapid t=").endsWith(", k=" + keys.publicKey());
  }

  @Test
  @DisplayName("No arranca si la clave pública no corresponde a la privada")
  void rejectsMismatchedKeyPair() {
    KeyPair one = WebPushCrypto.generateKeyPair();
    KeyPair other = WebPushCrypto.generateKeyPair();

    assertThatThrownBy(() -> new VapidKeys(properties(one.getPublic(), other.getPrivate())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("does not match");
  }

  @Test
  @DisplayName("Dice que las claves están intercambiadas sin enseñarlas")
  void detectsSwappedKeys() {
    KeyPair pair = WebPushCrypto.generateKeyPair();
    PushProperties right = properties(pair.getPublic(), pair.getPrivate());
    PushProperties swapped = new PushProperties(right.privateKey(), right.publicKey(), null,
        null, null, null, null, null);

    assertThatThrownBy(() -> new VapidKeys(swapped))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("swapped")
        .hasMessageNotContaining(right.privateKey());
  }

  @Test
  @DisplayName("Dice cuántos caracteres tiene una clave copiada a medias")
  void detectsIncompleteKey() {
    KeyPair pair = WebPushCrypto.generateKeyPair();
    PushProperties right = properties(pair.getPublic(), pair.getPrivate());
    PushProperties cut = new PushProperties(right.publicKey().substring(0, 60),
        right.privateKey(), null, null, null, null, null, null);

    assertThatThrownBy(() -> new VapidKeys(cut))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("VAPID_PUBLIC_KEY has 60 characters");
  }

  @Test
  @DisplayName("Detecta la línea entera pegada con el nombre de la variable")
  void detectsPastedVariableName() {
    KeyPair pair = WebPushCrypto.generateKeyPair();
    PushProperties right = properties(pair.getPublic(), pair.getPrivate());
    PushProperties pasted = new PushProperties("VAPID_PUBLIC_KEY=" + right.publicKey(),
        right.privateKey(), null, null, null, null, null, null);

    assertThatThrownBy(() -> new VapidKeys(pasted))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("without the variable name");
  }

  private static PushProperties properties(java.security.PublicKey publicKey,
                                           java.security.PrivateKey privateKey) {
    byte[] raw = ((ECPrivateKey) privateKey).getS().toByteArray();
    byte[] scalar = new byte[32];
    int length = Math.min(raw.length, 32);
    System.arraycopy(raw, raw.length - length, scalar, 32 - length, length);
    return new PushProperties(
        WebPushCrypto.encode(WebPushCrypto.rawPublicKey((ECPublicKey) publicKey)),
        WebPushCrypto.encode(Arrays.copyOf(scalar, 32)), null, null, null, null, null, null);
  }
}
