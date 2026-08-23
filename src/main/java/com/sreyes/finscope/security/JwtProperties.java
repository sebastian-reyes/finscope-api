package com.sreyes.finscope.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de la emisión y validación de tokens.
 * El secreto no tiene valor por defecto a propósito: la aplicación no debe arrancar con
 * una clave de firma conocida, por lo que debe aportarse siempre desde el entorno.
 *
 * @param secret          clave de firma HMAC, de al menos 32 caracteres para HS256
 * @param issuer          emisor que se registra en los tokens
 * @param accessTokenTtl  validez del token de acceso
 * @param refreshTokenTtl validez del token de refresco
 */
@ConfigurationProperties(prefix = "finscope.security.jwt")
public record JwtProperties(
    String secret,
    String issuer,
    Duration accessTokenTtl,
    Duration refreshTokenTtl) {
}
