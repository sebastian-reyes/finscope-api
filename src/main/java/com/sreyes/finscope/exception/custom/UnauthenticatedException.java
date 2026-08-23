package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando una operación requiere un usuario autenticado y la petición no lo aporta.
 */
public class UnauthenticatedException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code UnauthenticatedException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public UnauthenticatedException(String message) {
    super(message, "UNAUTHENTICATED", HttpStatus.UNAUTHORIZED);
  }
}
