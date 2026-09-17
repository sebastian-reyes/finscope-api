package com.sreyes.finscope.util.push;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.experimental.UtilityClass;

/**
 * Criptografía de Web Push: el cifrado del mensaje (RFC 8291) y la firma VAPID (RFC 8292).
 * Esta clase no debe ser instanciada.
 *
 * <p>Está escrita sobre la JDK y no sobre una librería de Web Push a propósito. Las que hay
 * para Java traen BouncyCastle y un cliente HTTP bloqueante, y la API corre en un contenedor
 * de 512 MB con la memoria repartida a mano en el Dockerfile, sobre WebFlux. Todo lo que
 * hace falta —ECDH en P-256, HMAC-SHA-256, AES-GCM y ECDSA— ya viene en la JDK, y la prueba
 * de esta clase reproduce byte a byte el ejemplo del apéndice A del RFC 8291: si algo de
 * aquí se aparta del estándar, esa prueba falla antes de que un navegador lo rechace.</p>
 *
 * <p>El servicio de push de cada navegador (el de Google, el de Apple, el de Mozilla) solo
 * transporta bytes cifrados: no puede leer el aviso. Lo descifra el propio navegador con la
 * clave privada que generó al suscribirse y que nunca sale del dispositivo.</p>
 */
@UtilityClass
public final class WebPushCrypto {

  /** Longitud de una clave pública P-256 sin comprimir: 0x04, X y Y. */
  public static final int PUBLIC_KEY_LENGTH = 65;

  /** Longitud de una clave privada P-256 en bruto. */
  public static final int PRIVATE_KEY_LENGTH = 32;

  /** Longitud del secreto de autenticación que genera el navegador al suscribirse. */
  public static final int AUTH_SECRET_LENGTH = 16;

  /**
   * Tamaño de registro que se declara en la cabecera del cuerpo cifrado.
   * Un aviso cabe siempre en un único registro, así que el valor solo tiene que ser mayor
   * que el mensaje; 4096 es el que usa el propio RFC y el que esperan los servicios.
   */
  private static final int RECORD_SIZE = 4096;

  private static final int SALT_LENGTH = 16;
  private static final int KEY_LENGTH = 16;
  private static final int NONCE_LENGTH = 12;
  private static final int TAG_BITS = 128;

  /** Delimitador del último registro: el mensaje entero va en uno solo. */
  private static final byte LAST_RECORD = 0x02;

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  /**
   * Parámetros de la curva P-256, resueltos una vez.
   * Son los de {@code secp256r1}, que es la única curva que admite Web Push.
   */
  private static final ECParameterSpec P256 = curve();

  /**
   * Cifra un mensaje para una suscripción con una clave efímera y una sal nuevas.
   *
   * @param plaintext  contenido del aviso
   * @param uaPublic   clave pública del navegador ({@code keys.p256dh}), sin comprimir
   * @param authSecret secreto de autenticación del navegador ({@code keys.auth})
   * @return el cuerpo listo para mandarse con {@code Content-Encoding: aes128gcm}
   */
  public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
    byte[] salt = new byte[SALT_LENGTH];
    RANDOM.nextBytes(salt);
    return encrypt(plaintext, uaPublic, authSecret, generateKeyPair(), salt);
  }

  /**
   * Cifra un mensaje con la clave efímera y la sal indicadas.
   * Existe separado del anterior solo para que las pruebas puedan fijar los dos valores
   * aleatorios y comparar con el ejemplo del RFC; fuera de ellas no hay motivo para usarlo.
   *
   * @param plaintext  contenido del aviso
   * @param uaPublic   clave pública del navegador, sin comprimir
   * @param authSecret secreto de autenticación del navegador
   * @param ephemeral  par de claves del servidor para este mensaje
   * @param salt       sal de 16 bytes
   * @return el cuerpo cifrado con su cabecera
   */
  static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair ephemeral,
                        byte[] salt) {
    requireLength("p256dh", uaPublic, PUBLIC_KEY_LENGTH);
    requireLength("auth", authSecret, AUTH_SECRET_LENGTH);
    try {
      byte[] asPublic = rawPublicKey((ECPublicKey) ephemeral.getPublic());

      KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
      agreement.init(ephemeral.getPrivate());
      agreement.doPhase(publicKey(uaPublic), true);
      byte[] ecdhSecret = agreement.generateSecret();

      // Primero se mezcla el secreto compartido con el de autenticación del navegador y con
      // las dos claves públicas: así el cifrado queda atado a esta suscripción concreta y a
      // este remitente, y no solo al acuerdo de claves.
      byte[] prkKey = hmac(authSecret, ecdhSecret);
      byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII),
          new byte[] {0}, uaPublic, asPublic, new byte[] {1});
      byte[] ikm = hmac(prkKey, keyInfo);

      byte[] prk = hmac(salt, ikm);
      byte[] cek = Arrays.copyOf(hmac(prk, info("Content-Encoding: aes128gcm")), KEY_LENGTH);
      byte[] nonce = Arrays.copyOf(hmac(prk, info("Content-Encoding: nonce")), NONCE_LENGTH);

      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"),
          new GCMParameterSpec(TAG_BITS, nonce));
      byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[] {LAST_RECORD}));

      ByteBuffer header = ByteBuffer.allocate(SALT_LENGTH + 4 + 1 + asPublic.length);
      header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
      return concat(header.array(), ciphertext);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Could not encrypt the push message", ex);
    }
  }

  /**
   * Firma el token VAPID que acredita al servidor ante el servicio de push.
   *
   * <p>El servicio comprueba la firma con la clave pública que el navegador recibió al
   * suscribirse, así que solo quien tiene la privada puede mandar avisos a esa suscripción
   * aunque alguien conozca su dirección.</p>
   *
   * @param audience   origen del servicio de push, por ejemplo {@code https://fcm.googleapis.com}
   * @param subject    contacto del remitente, {@code mailto:} o {@code https:}
   * @param expiresAt  instante de caducidad en segundos desde la época; como mucho 24 h
   * @param privateKey clave privada VAPID
   * @return el JWT firmado con ES256
   */
  public static String vapidToken(String audience, String subject, long expiresAt,
                                  PrivateKey privateKey) {
    String header = encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}"
        .getBytes(StandardCharsets.UTF_8));
    String claims = encode(("{\"aud\":\"" + jsonEscape(audience) + "\",\"exp\":" + expiresAt
        + ",\"sub\":\"" + jsonEscape(subject) + "\"}").getBytes(StandardCharsets.UTF_8));
    String signingInput = header + "." + claims;
    return signingInput + "." + encode(sign(signingInput, privateKey));
  }

  /**
   * Firma un texto con ECDSA sobre P-256 y SHA-256.
   * La firma sale en el formato de JWS —R y S concatenados, 64 bytes— y no en DER, que es lo
   * que devolvería el algoritmo por defecto y lo que un servicio de push rechazaría.
   *
   * @param input      texto a firmar
   * @param privateKey clave privada
   * @return la firma de 64 bytes
   */
  public static byte[] sign(String input, PrivateKey privateKey) {
    try {
      Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
      signer.initSign(privateKey);
      signer.update(input.getBytes(StandardCharsets.US_ASCII));
      return signer.sign();
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Could not sign the VAPID token", ex);
    }
  }

  /**
   * Comprueba una firma hecha con {@link #sign}.
   *
   * @param input     texto firmado
   * @param signature firma de 64 bytes
   * @param publicKey clave pública
   * @return si la firma corresponde al texto y a la clave
   */
  public static boolean verify(String input, byte[] signature, PublicKey publicKey) {
    try {
      Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
      verifier.initVerify(publicKey);
      verifier.update(input.getBytes(StandardCharsets.US_ASCII));
      return verifier.verify(signature);
    } catch (GeneralSecurityException ex) {
      return false;
    }
  }

  /**
   * Reconstruye una clave pública P-256 a partir de su forma sin comprimir.
   *
   * @param raw 65 bytes: 0x04, X y Y
   * @return la clave pública
   * @throws IllegalArgumentException si no es un punto válido de la curva
   */
  public static ECPublicKey publicKey(byte[] raw) {
    requireLength("public key", raw, PUBLIC_KEY_LENGTH);
    if (raw[0] != 0x04) {
      throw new IllegalArgumentException("The public key must be an uncompressed P-256 point");
    }
    try {
      ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(raw, 1, 33)),
          new BigInteger(1, Arrays.copyOfRange(raw, 33, 65)));
      return (ECPublicKey) KeyFactory.getInstance("EC")
          .generatePublic(new ECPublicKeySpec(point, P256));
    } catch (GeneralSecurityException ex) {
      throw new IllegalArgumentException("The public key is not a valid P-256 point", ex);
    }
  }

  /**
   * Reconstruye una clave privada P-256 a partir de su escalar en bruto.
   *
   * @param raw 32 bytes
   * @return la clave privada
   */
  public static PrivateKey privateKey(byte[] raw) {
    requireLength("private key", raw, PRIVATE_KEY_LENGTH);
    try {
      return KeyFactory.getInstance("EC")
          .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
    } catch (GeneralSecurityException ex) {
      throw new IllegalArgumentException("The private key is not a valid P-256 scalar", ex);
    }
  }

  /**
   * Escribe una clave pública en la forma sin comprimir que usan Web Push y los navegadores.
   *
   * @param key clave pública
   * @return 65 bytes: 0x04, X y Y, cada coordenada rellenada a 32 bytes
   */
  public static byte[] rawPublicKey(ECPublicKey key) {
    return concat(new byte[] {0x04}, unsigned(key.getW().getAffineX()),
        unsigned(key.getW().getAffineY()));
  }

  /**
   * Genera un par de claves P-256.
   *
   * @return el par recién generado
   */
  public static KeyPair generateKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
      return generator.generateKeyPair();
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("P-256 is not available in this JVM", ex);
    }
  }

  /**
   * Codifica en Base64 URL sin relleno, que es la forma de todas las claves de Web Push.
   *
   * @param bytes bytes a codificar
   * @return el texto codificado
   */
  public static String encode(byte[] bytes) {
    return URL_ENCODER.encodeToString(bytes);
  }

  /**
   * Decodifica Base64 URL, con o sin relleno.
   *
   * @param text texto codificado
   * @return los bytes
   * @throws IllegalArgumentException si el texto no es Base64 URL
   */
  public static byte[] decode(String text) {
    return URL_DECODER.decode(text.strip());
  }

  private static byte[] info(String label) {
    return concat(label.getBytes(StandardCharsets.US_ASCII), new byte[] {0, 1});
  }

  private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data);
  }

  private static byte[] unsigned(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length == 32) {
      return bytes;
    }
    byte[] padded = new byte[32];
    int length = Math.min(bytes.length, 32);
    System.arraycopy(bytes, bytes.length - length, padded, 32 - length, length);
    return padded;
  }

  private static byte[] concat(byte[]... parts) {
    int length = 0;
    for (byte[] part : parts) {
      length += part.length;
    }
    ByteBuffer buffer = ByteBuffer.allocate(length);
    for (byte[] part : parts) {
      buffer.put(part);
    }
    return buffer.array();
  }

  private static void requireLength(String name, byte[] value, int length) {
    if (value == null || value.length != length) {
      throw new IllegalArgumentException(name + " must be " + length + " bytes long");
    }
  }

  private static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static ECParameterSpec curve() {
    try {
      AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
      parameters.init(new ECGenParameterSpec("secp256r1"));
      return parameters.getParameterSpec(ECParameterSpec.class);
    } catch (GeneralSecurityException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }
}
