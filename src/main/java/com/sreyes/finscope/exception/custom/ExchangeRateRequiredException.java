package com.sreyes.finscope.exception.custom;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada que se lanza cuando se registra un importe en una moneda que no
 * es la base sin indicar el tipo de cambio con el que se apuntó.
 * Sin él, ese importe no podría volver a leerse en moneda base nunca más: el cambio del día
 * en que ocurrió el movimiento no se puede reconstruir después.
 */
public class ExchangeRateRequiredException extends BusinessException {

  /**
   * Crea una nueva instancia de {@code ExchangeRateRequiredException} con el mensaje
   * especificado.
   *
   * @param message el mensaje descriptivo del error.
   */
  public ExchangeRateRequiredException(String message) {
    super(message, "EXCHANGE_RATE_REQUIRED", HttpStatus.BAD_REQUEST);
  }
}
