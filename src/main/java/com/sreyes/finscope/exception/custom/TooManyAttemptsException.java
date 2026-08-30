package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando se acumulan demasiados intentos de acceso
 * fallidos y el acceso queda bloqueado temporalmente.
 */
public class TooManyAttemptsException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code TooManyAttemptsException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public TooManyAttemptsException(String message) {
    super(message, "TOO_MANY_ATTEMPTS", HttpStatus.TOO_MANY_REQUESTS);
  }
}
