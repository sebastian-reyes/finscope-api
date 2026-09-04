package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al confirmar un movimiento fijo en un mes que ya
 * estaba confirmado.
 * Se rechaza en lugar de registrar un segundo movimiento porque el mes ya tiene el suyo, y
 * duplicarlo contaría el alquiler dos veces en el resumen sin que nadie lo pidiera. Para
 * deshacer una confirmación hay que borrar el movimiento.
 */
public class RecurringAlreadyConfirmedException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code RecurringAlreadyConfirmedException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public RecurringAlreadyConfirmedException(String message) {
    super(message, "RECURRING_ALREADY_CONFIRMED", HttpStatus.CONFLICT);
  }
}
