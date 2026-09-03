package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando no se encuentra un presupuesto del usuario.
 * Un presupuesto de otra cuenta se comporta igual que uno inexistente.
 */
public class BudgetNotFoundException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code BudgetNotFoundException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public BudgetNotFoundException(String message) {
    super(message, "BUDGET_NOT_FOUND", HttpStatus.NOT_FOUND);
  }
}
