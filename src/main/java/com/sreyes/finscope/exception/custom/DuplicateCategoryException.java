package com.sreyes.finscope.exception.custom;

/**
 * Excepción personalizada que se lanza cuando se intenta crear o actualizar una categoría
 * que ya existe en la base de datos.
 */
public class DuplicateCategoryException extends RuntimeException {

  /**
   * Crea una nueva instancia de {@code DuplicateCategoryException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public DuplicateCategoryException(String message) {
    super(message);
  }
}
