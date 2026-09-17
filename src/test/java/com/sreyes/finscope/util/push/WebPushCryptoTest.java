package com.sreyes.finscope.util.push;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link WebPushCrypto}.
 *
 * La primera es la que importa: reproduce el ejemplo del apéndice A del RFC 8291 con sus
 * mismas claves y su misma sal, y exige el mismo cuerpo byte a byte. Un navegador no dice por
 * qué rechaza un mensaje mal cifrado —simplemente no lo enseña—, así que esta es la única
 * forma de saber que el cifrado es el del estándar sin tener un teléfono delante.
 */
class WebPushCryptoTest {

  private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
  private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
  private static final String AS_PUBLIC =
      "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
  private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
  private static final String UA_PUBLIC =
      "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
  private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
  private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
  private static final String EXPECTED_BODY =
      "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYW"
          + "AmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexS"
          + "gSxsj_Qulcy4a-fN";

  @Test
  @DisplayName("Reproduce byte a byte el ejemplo del apéndice A del RFC 8291")
  void matchesRfc8291Example() {
    KeyPair ephemeral = new KeyPair(WebPushCrypto.publicKey(WebPushCrypto.decode(AS_PUBLIC)),
        WebPushCrypto.privateKey(WebPushCrypto.decode(AS_PRIVATE)));

    byte[] body = WebPushCrypto.encrypt(PLAINTEXT.getBytes(StandardCharsets.UTF_8),
        WebPushCrypto.decode(UA_PUBLIC), WebPushCrypto.decode(AUTH_SECRET), ephemeral,
        WebPushCrypto.decode(SALT));

    assertEquals(EXPECTED_BODY, WebPushCrypto.encode(body));
  }

  @Test
  @DisplayName("El navegador descifra lo cifrado con clave y sal aleatorias")
  void roundTripsWithFreshKeys() throws Exception {
    KeyPair browser = WebPushCrypto.generateKeyPair();
    byte[] uaPublic = WebPushCrypto.rawPublicKey((ECPublicKey) browser.getPublic());
    byte[] authSecret = new byte[16];
    new java.security.SecureRandom().nextBytes(authSecret);
    byte[] message = "{\"notification\":{\"title\":\"Mañana vence el alquiler\"}}"
        .getBytes(StandardCharsets.UTF_8);

    byte[] body = WebPushCrypto.encrypt(message, uaPublic, authSecret);

    assertArrayEquals(message, decrypt(body, browser.getPrivate(), uaPublic, authSecret));
  }

  @Test
  @DisplayName("Cada envío lleva sal y clave efímera nuevas")
  void neverRepeatsCiphertext() {
    byte[] uaPublic = WebPushCrypto.decode(UA_PUBLIC);
    byte[] authSecret = WebPushCrypto.decode(AUTH_SECRET);
    byte[] message = PLAINTEXT.getBytes(StandardCharsets.UTF_8);

    byte[] first = WebPushCrypto.encrypt(message, uaPublic, authSecret);
    byte[] second = WebPushCrypto.encrypt(message, uaPublic, authSecret);

    assertFalse(Arrays.equals(first, second));
  }

  @Test
  @DisplayName("Rechaza claves del navegador con la longitud equivocada")
  void rejectsMalformedBrowserKeys() {
    byte[] message = PLAINTEXT.getBytes(StandardCharsets.UTF_8);

    assertThrows(IllegalArgumentException.class,
        () -> WebPushCrypto.encrypt(message, new byte[64], WebPushCrypto.decode(AUTH_SECRET)));
    assertThrows(IllegalArgumentException.class,
        () -> WebPushCrypto.encrypt(message, WebPushCrypto.decode(UA_PUBLIC), new byte[8]));
  }

  @Test
  @DisplayName("El token VAPID se verifica con la clave pública y no con otra")
  void signsVerifiableVapidToken() {
    KeyPair vapid = WebPushCrypto.generateKeyPair();

    String token = WebPushCrypto.vapidToken("https://fcm.googleapis.com",
        "mailto:no-reply@fin-scope.app", 1_900_000_000L, vapid.getPrivate());

    String[] parts = token.split("\\.");
    assertEquals(3, parts.length);
    String claims = new String(WebPushCrypto.decode(parts[1]), StandardCharsets.UTF_8);
    assertEquals("{\"aud\":\"https://fcm.googleapis.com\",\"exp\":1900000000,"
        + "\"sub\":\"mailto:no-reply@fin-scope.app\"}", claims);
    byte[] signature = WebPushCrypto.decode(parts[2]);
    assertEquals(64, signature.length);
    assertTrue(WebPushCrypto.verify(parts[0] + "." + parts[1], signature, vapid.getPublic()));
    assertFalse(WebPushCrypto.verify(parts[0] + "." + parts[1], signature,
        WebPushCrypto.generateKeyPair().getPublic()));
  }

  /**
   * Descifra como lo hace el navegador: con su clave privada y la pública que viaja en la
   * cabecera del propio mensaje. Es una segunda implementación de la misma derivación, así
   * que la ida y vuelta solo sale bien si las dos siguen el estándar al mismo tiempo.
   */
  private static byte[] decrypt(byte[] body, PrivateKey uaPrivate, byte[] uaPublic,
                                byte[] authSecret) throws Exception {
    ByteBuffer buffer = ByteBuffer.wrap(body);
    byte[] salt = new byte[16];
    buffer.get(salt);
    assertEquals(4096, buffer.getInt());
    byte[] asPublic = new byte[buffer.get()];
    buffer.get(asPublic);
    byte[] ciphertext = new byte[buffer.remaining()];
    buffer.get(ciphertext);

    KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
    agreement.init(uaPrivate);
    agreement.doPhase(WebPushCrypto.publicKey(asPublic), true);
    byte[] prkKey = hmac(authSecret, agreement.generateSecret());
    ByteBuffer keyInfo = ByteBuffer.allocate(14 + 65 + 65 + 1);
    keyInfo.put("WebPush: info".getBytes(StandardCharsets.US_ASCII)).put((byte) 0)
        .put(uaPublic).put(asPublic).put((byte) 1);
    byte[] prk = hmac(salt, hmac(prkKey, keyInfo.array()));
    byte[] cek = Arrays.copyOf(hmac(prk, label("Content-Encoding: aes128gcm")), 16);
    byte[] nonce = Arrays.copyOf(hmac(prk, label("Content-Encoding: nonce")), 12);

    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"),
        new GCMParameterSpec(128, nonce));
    byte[] padded = cipher.doFinal(ciphertext);
    assertEquals(2, padded[padded.length - 1]);
    return Arrays.copyOf(padded, padded.length - 1);
  }

  private static byte[] label(String text) {
    byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
    byte[] out = Arrays.copyOf(bytes, bytes.length + 2);
    out[bytes.length + 1] = 1;
    return out;
  }

  private static byte[] hmac(byte[] key, byte[] data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data);
  }
}
