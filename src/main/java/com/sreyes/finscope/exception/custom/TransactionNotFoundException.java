package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando no se encuentra una transacción.
 */
public class TransactionNotFoundException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code TransactionNotFoundException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public TransactionNotFoundException(String message) {
    super(message, "TRANSACTION_NOT_FOUND", HttpStatus.NOT_FOUND);
  }
}
