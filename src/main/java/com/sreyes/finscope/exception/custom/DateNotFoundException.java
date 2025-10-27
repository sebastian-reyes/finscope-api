package com.sreyes.finscope.exception.custom;

/**
 * Excepción personalizada que se lanza cuando no se encuentra una fecha específica.
 */
public class DateNotFoundException extends RuntimeException {

  /**
   * Crea una nueva instancia de {@code DateNotFoundException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public DateNotFoundException(String message) {
    super(message);
  }
}