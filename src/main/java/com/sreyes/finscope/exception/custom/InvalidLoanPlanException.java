package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando el plan de cuotas de un préstamo no cuadra: no
 * suma lo que hay que devolver, alguna cuota vence antes de prestar el dinero, o el total
 * queda por debajo de lo ya devuelto.
 */
public class InvalidLoanPlanException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code InvalidLoanPlanException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public InvalidLoanPlanException(String message) {
    super(message, "INVALID_LOAN_PLAN", HttpStatus.BAD_REQUEST);
  }
}
