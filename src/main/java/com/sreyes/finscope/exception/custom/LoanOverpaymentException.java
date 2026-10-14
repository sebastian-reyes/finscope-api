package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al registrar un pago mayor que lo que falta por
 * devolver. Se rechaza en lugar de aceptarlo porque casi siempre es un importe mal escrito,
 * y aceptarlo dejaría un saldo negativo que no significa nada.
 */
public class LoanOverpaymentException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code LoanOverpaymentException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public LoanOverpaymentException(String message) {
    super(message, "LOAN_OVERPAYMENT", HttpStatus.CONFLICT);
  }
}
