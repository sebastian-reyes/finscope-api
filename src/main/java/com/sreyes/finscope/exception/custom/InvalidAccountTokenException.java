package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando un enlace recibido por correo no existe, no es
 * del propósito esperado, ya se ha usado o ha caducado.
 */
public class InvalidAccountTokenException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code InvalidAccountTokenException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public InvalidAccountTokenException(String message) {
    super(message, "INVALID_ACCOUNT_TOKEN", HttpStatus.BAD_REQUEST);
  }
}
