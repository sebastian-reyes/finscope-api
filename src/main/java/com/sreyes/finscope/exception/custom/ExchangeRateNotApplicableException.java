package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando se indica un tipo de cambio para un importe
 * que ya está en la moneda base.
 * Guardarlo diría que se convirtió algo que nadie convirtió, y dejaría dos cifras
 * discrepando sobre el mismo movimiento.
 */
public class ExchangeRateNotApplicableException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code ExchangeRateNotApplicableException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public ExchangeRateNotApplicableException(String message) {
    super(message, "EXCHANGE_RATE_NOT_APPLICABLE", HttpStatus.BAD_REQUEST);
  }
}
