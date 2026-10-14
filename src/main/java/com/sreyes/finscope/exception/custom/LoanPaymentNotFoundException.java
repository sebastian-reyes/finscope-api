package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando no se encuentra un pago dentro de un préstamo.
 * Un pago de otro préstamo se comporta igual que uno inexistente.
 */
public class LoanPaymentNotFoundException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code LoanPaymentNotFoundException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public LoanPaymentNotFoundException(String message) {
    super(message, "LOAN_PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND);
  }
}
