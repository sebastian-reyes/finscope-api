package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando el criterio de ordenamiento solicitado no está
 * admitido.
 */
public class InvalidSortException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code InvalidSortException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public InvalidSortException(String message) {
    super(message, "INVALID_SORT", HttpStatus.BAD_REQUEST);
  }
}
