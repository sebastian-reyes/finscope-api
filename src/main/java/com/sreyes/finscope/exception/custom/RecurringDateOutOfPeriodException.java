package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al confirmar un movimiento fijo con una fecha que
 * cae fuera del mes que se está confirmando.
 * Se rechaza porque el estado del fijo se resuelve buscando su movimiento dentro del mes:
 * un movimiento fechado fuera dejaría el mes como pendiente para siempre y permitiría
 * confirmarlo otra vez, contando el mismo cargo dos veces.
 */
public class RecurringDateOutOfPeriodException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code RecurringDateOutOfPeriodException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public RecurringDateOutOfPeriodException(String message) {
    super(message, "RECURRING_DATE_OUT_OF_PERIOD", HttpStatus.BAD_REQUEST);
  }
}
