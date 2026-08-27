package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al intentar eliminar la categoría de reserva del
 * usuario.
 * Es la que recibe los movimientos de las categorías eliminadas, así que borrarla dejaría
 * sin destino a la siguiente eliminación y sin categoría a sus transacciones, que la
 * necesitan obligatoriamente.
 */
public class SystemCategoryException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code SystemCategoryException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public SystemCategoryException(String message) {
    super(message, "SYSTEM_CATEGORY_PROTECTED", HttpStatus.CONFLICT);
  }
}
