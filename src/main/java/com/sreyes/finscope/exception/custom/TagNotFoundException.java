package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando no se encuentra un tag del usuario.
 */
public class TagNotFoundException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code TagNotFoundException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public TagNotFoundException(String message) {
    super(message, "TAG_NOT_FOUND", HttpStatus.NOT_FOUND);
  }
}
