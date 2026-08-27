package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza al dar a un tag un nombre que el usuario ya tiene.
 * Se rechaza en lugar de fusionar los dos tags porque la fusión no tiene vuelta atrás: el
 * usuario perdería la distinción entre ambos sin haberlo pedido.
 */
public class TagNameAlreadyUsedException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code TagNameAlreadyUsedException} con el mensaje especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public TagNameAlreadyUsedException(String message) {
    super(message, "TAG_NAME_ALREADY_USED", HttpStatus.CONFLICT);
  }
}
