package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando no se encuentra un movimiento fijo del
 * usuario.
 * Una plantilla de otra cuenta se comporta igual que una inexistente.
 */
public class RecurringNotFoundException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code RecurringNotFoundException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public RecurringNotFoundException(String message) {
    super(message, "RECURRING_NOT_FOUND", HttpStatus.NOT_FOUND);
  }
}
