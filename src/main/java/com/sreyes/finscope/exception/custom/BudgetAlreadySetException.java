package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al presupuestar una categoría que ya tiene
 * presupuesto en ese mes.
 * Se rechaza en lugar de sumar los dos importes o de pisar el anterior, para que fijar un
 * presupuesto nunca cambie en silencio uno que ya estaba puesto.
 */
public class BudgetAlreadySetException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code BudgetAlreadySetException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public BudgetAlreadySetException(String message) {
    super(message, "BUDGET_ALREADY_SET", HttpStatus.CONFLICT);
  }
}
