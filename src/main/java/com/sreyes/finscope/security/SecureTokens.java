package com.sreyes.finscope.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import lombok.experimental.UtilityClass;

/**
 * Genera y resume los valores aleatorios que la aplicación entrega como credencial.
 * Los usan el token de refresco y los enlaces que se mandan por correo, que son la misma
 * pieza: un valor que nadie puede adivinar, del que solo se guarda su hash y que se busca
 * por él.
 *
 * <p>El resumen es SHA-256 y no BCrypt a propósito. BCrypt es lento para que probar
 * contraseñas salga caro, pero aquí no hay nada que probar: el valor lo genera el servidor
 * con 32 bytes de entropía, así que no existe diccionario contra el que ensayarlo. Además
 * el hash tiene que ser determinista para poder buscar por él, cosa que BCrypt no permite
 * porque incorpora una sal distinta en cada cálculo.</p>
 */
@UtilityClass
public class SecureTokens {

  /**
   * Entropía de cada valor generado. Con 256 bits, adivinar uno no es un ataque viable.
   */
  private static final int TOKEN_BYTES = 32;

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /**
   * Genera un valor aleatorio apto para viajar en una URL.
   *
   * @return el valor del token, en Base64 sin relleno
   */
  public static String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Calcula el hash con el que se almacena un token.
   *
   * @param token valor del token
   * @return el hash SHA-256 en hexadecimal
   */
  public static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is required to store tokens", ex);
    }
  }
}
