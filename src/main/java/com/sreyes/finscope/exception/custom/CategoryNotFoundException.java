package com.sreyes.finscope.exception.custom;

/**
 * Excepción personalizada que se lanza cuando no se encuentra una categoría.
 */
public class CategoryNotFoundException extends RuntimeException {

  /**
   * Crea una nueva instancia de {@code CategoryNotFoundException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public CategoryNotFoundException(String message) {
    super(message);
  }
}