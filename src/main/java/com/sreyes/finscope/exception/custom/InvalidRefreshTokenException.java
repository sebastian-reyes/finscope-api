package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando un token de refresco no existe, ya fue consumido, fue revocado o ha caducado.
 */
public class InvalidRefreshTokenException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code InvalidRefreshTokenException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public InvalidRefreshTokenException(String message) {
    super(message, "INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
  }
}
