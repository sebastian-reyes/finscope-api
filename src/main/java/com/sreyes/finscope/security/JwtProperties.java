package com.sreyes.finscope.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la emisión y validación de tokens.
 * El secreto no tiene valor por defecto a propósito: la aplicación no debe arrancar con
 * una clave de firma conocida, por lo que debe aportarse siempre desde el entorno.
 *
 * @param secret          clave de firma HMAC, de al menos 32 caracteres para HS256
 * @param issuer          emisor que se registra en los tokens
 * @param audience        destinatario que se registra en los tokens
 * @param accessTokenTtl  validez del token de acceso
 * @param refreshTokenTtl validez del token de refresco
 */
@ConfigurationProperties(prefix = "finscope.security.jwt")
public record JwtProperties(
    String secret,
    String issuer,
    String audience,
    Duration accessTokenTtl,
    Duration refreshTokenTtl) {

  /**
   * Longitud mínima de la clave para que HS256 conserve la fuerza del algoritmo.
   * Una clave más corta que el propio digest deja la firma por debajo de sus 256 bits.
   */
  private static final int MIN_SECRET_BYTES = 32;

  /**
   * Comprueba al arrancar que la configuración de los tokens es utilizable.
   * Se valida aquí, y no al firmar el primer token, porque una clave débil o una validez
   * indefinida son un fallo de despliegue: es preferible que la aplicación no arranque a
   * que emita tokens que no protegen nada.
   */
  public JwtProperties {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "finscope.security.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
    }
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalStateException("finscope.security.jwt.issuer must be configured");
    }
    if (audience == null || audience.isBlank()) {
      throw new IllegalStateException("finscope.security.jwt.audience must be configured");
    }
    if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
      throw new IllegalStateException(
          "finscope.security.jwt.access-token-ttl must be a positive duration");
    }
    if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
      throw new IllegalStateException(
          "finscope.security.jwt.refresh-token-ttl must be a positive duration");
    }
  }
}
