package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al confirmar u omitir un movimiento fijo en un mes
 * en el que no vence.
 * Pasa cuando la plantilla está pausada, cuando el mes es anterior a su arranque o cuando
 * toca cada varios meses y ese no es uno de ellos. Es un error de la petición y no un
 * conflicto: no hay nada en ese mes con lo que chocar.
 */
public class RecurringNotDueException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code RecurringNotDueException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public RecurringNotDueException(String message) {
    super(message, "RECURRING_NOT_DUE", HttpStatus.BAD_REQUEST);
  }
}
