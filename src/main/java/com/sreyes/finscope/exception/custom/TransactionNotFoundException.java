package com.sreyes.finscope.exception.custom;

/**
 * Excepción personalizada que se lanza cuando no se encuentra una transacción.
 */
public class TransactionNotFoundException extends RuntimeException {

  /**
   * Crea una nueva instancia de {@code TransactionNotFoundException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public TransactionNotFoundException(String message) {
    super(message);
  }
}