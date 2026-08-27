package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando no se encuentra una categoría del usuario.
 * Una categoría de otra cuenta se comporta igual que una inexistente.
 */
public class CategoryNotFoundException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code CategoryNotFoundException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public CategoryNotFoundException(String message) {
    super(message, "CATEGORY_NOT_FOUND", HttpStatus.NOT_FOUND);
  }
}
