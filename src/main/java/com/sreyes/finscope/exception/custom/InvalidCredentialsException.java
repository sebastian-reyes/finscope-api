package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando el correo o la contraseña no son válidos.
 */
public class InvalidCredentialsException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code InvalidCredentialsException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public InvalidCredentialsException(String message) {
    super(message, "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
  }
}
