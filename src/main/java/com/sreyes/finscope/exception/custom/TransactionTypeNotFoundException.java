package com.sreyes.finscope.exception.custom;

/**
 * Excepción personalizada que se lanza cuando no se encuentra un tipo de transacción.
 */
public class TransactionTypeNotFoundException extends RuntimeException {

  /**
   * Crea una nueva instancia de {@code TransactionTypeNotFoundException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public TransactionTypeNotFoundException(String message) {
    super(message);
  }
}