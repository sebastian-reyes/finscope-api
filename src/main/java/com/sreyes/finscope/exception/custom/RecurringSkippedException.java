package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al confirmar un movimiento fijo en un mes que el
 * usuario había omitido.
 * Son dos decisiones contrarias sobre el mismo mes, y resolverla en silencio a favor de
 * cualquiera de las dos dejaría al usuario sin saber cuál ganó: primero se deshace la
 * omisión y después se confirma.
 */
public class RecurringSkippedException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code RecurringSkippedException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public RecurringSkippedException(String message) {
    super(message, "RECURRING_SKIPPED", HttpStatus.CONFLICT);
  }
}
