package com.sreyes.finscope.exception.custom;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepción base de las reglas de negocio de la aplicación.
 * Cada excepción concreta aporta un código estable, pensado para que los clientes de la
 * API reaccionen sin depender del mensaje, y el estado HTTP con el que debe responderse.
 */
@Getter
public abstract class BusinessException extends RuntimeException {

  /**
   * Código estable del error, por ejemplo TRANSACTION_NOT_FOUND.
   */
  private final String code;

  /**
   * Estado HTTP con el que debe responderse la petición.
   */
  private final transient HttpStatus status;

  /**
   * Crea una nueva instancia con el mensaje, código y estado indicados.
   *
   * @param message el mensaje descriptivo del error.
   * @param code    el código estable del error.
   * @param status  el estado HTTP asociado al error.
   */
  protected BusinessException(String message, String code, HttpStatus status) {
    super(message);
    this.code = code;
    this.status = status;
  }
}
