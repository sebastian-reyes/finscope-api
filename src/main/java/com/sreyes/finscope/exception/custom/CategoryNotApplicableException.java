package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando la categoría elegida no admite el tipo de
 * la transacción, por ejemplo al clasificar un egreso con una categoría de ingresos.
 */
public class CategoryNotApplicableException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code CategoryNotApplicableException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public CategoryNotApplicableException(String message) {
    super(message, "CATEGORY_NOT_APPLICABLE", HttpStatus.BAD_REQUEST);
  }
}
