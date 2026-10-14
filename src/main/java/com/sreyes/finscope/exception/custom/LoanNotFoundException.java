package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando no se encuentra un préstamo del usuario.
 * Un préstamo de otra cuenta se comporta igual que uno inexistente.
 */
public class LoanNotFoundException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code LoanNotFoundException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public LoanNotFoundException(String message) {
    super(message, "LOAN_NOT_FOUND", HttpStatus.NOT_FOUND);
  }
}
