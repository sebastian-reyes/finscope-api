package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al intentar ocupar un nombre de categoría que el
 * usuario ya tiene.
 * Se rechaza en lugar de fusionar las dos categorías, para que la operación nunca
 * reclasifique movimientos sin avisar.
 */
public class CategoryNameAlreadyUsedException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code CategoryNameAlreadyUsedException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public CategoryNameAlreadyUsedException(String message) {
    super(message, "CATEGORY_NAME_ALREADY_USED", HttpStatus.CONFLICT);
  }
}
