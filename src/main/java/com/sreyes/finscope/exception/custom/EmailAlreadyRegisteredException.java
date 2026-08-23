package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al registrar un correo que ya tiene una cuenta con contraseña.
 */
public class EmailAlreadyRegisteredException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code EmailAlreadyRegisteredException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public EmailAlreadyRegisteredException(String message) {
    super(message, "EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT);
  }
}
