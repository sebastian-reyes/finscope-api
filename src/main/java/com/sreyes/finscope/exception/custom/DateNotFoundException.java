package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando los filtros de fecha de una consulta no son
 * válidos o resultan incompatibles entre sí.
 */
public class DateNotFoundException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code DateNotFoundException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public DateNotFoundException(String message) {
    super(message, "INVALID_DATE_FILTER", HttpStatus.BAD_REQUEST);
  }
}
