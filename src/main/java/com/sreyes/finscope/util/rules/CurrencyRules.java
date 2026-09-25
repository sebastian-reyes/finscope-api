package com.sreyes.finscope.util.rules;

import com.sreyes.finscope.api.model.Currency;
import com.sreyes.finscope.exception.custom.ExchangeRateNotApplicableException;
import com.sreyes.finscope.exception.custom.ExchangeRateRequiredException;
import com.sreyes.finscope.util.constants.Constants;
import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

/**
 * Reglas de la moneda de un movimiento y de su tipo de cambio.
 * Esta clase no debe ser instanciada.
 *
 * La moneda y el tipo de cambio son una pareja y no dos campos sueltos: un importe en una
 * moneda que no es la base sin el cambio con el que se apuntó no se puede leer en soles
 * nunca más, y un importe en la moneda base con un cambio guardado dice que se convirtió
 * algo que nadie convirtió. Las dos situaciones son estados imposibles, así que se
 * comprueban juntas y en un solo sitio, que es el que usan el alta y la modificación.
 *
 * <p>La base de datos tiene la misma regla escrita como restricción. No sobra: aquí se
 * decide qué error ve el usuario, allí se impide que el estado llegue a existir aunque la
 * fila se escriba desde fuera de la aplicación.</p>
 */
@UtilityClass
public final class CurrencyRules {

  /**
   * Moneda base de la aplicación, la única que no se convierte a sí misma.
   * Es también la que se asume cuando una petición no dice ninguna, que es lo que
   * significaba un importe antes de que la moneda existiera.
   */
  public static final Currency BASE = Currency.PEN;

  /**
   * Decide si una moneda es la base.
   *
   * @param currency moneda a comprobar
   * @return si se trata de la moneda base
   */
  public static boolean isBase(Currency currency) {
    return BASE == currency;
  }

  /**
   * Resuelve la moneda de una petición que puede no traerla.
   * Un cuerpo sin moneda significa moneda base: es lo que valía antes de que el campo
   * existiera, y así una petición escrita contra el contrato anterior sigue registrando lo
   * mismo que registraba.
   *
   * @param currency moneda recibida, puede ser nula
   * @return la moneda recibida, o la base si no venía ninguna
   */
  public static Currency orBase(Currency currency) {
    return currency == null ? BASE : currency;
  }

  /**
   * Comprueba que la moneda y el tipo de cambio formen una pareja válida.
   *
   * @param currency     moneda con la que queda el movimiento
   * @param exchangeRate tipo de cambio con el que queda el movimiento, puede ser nulo
   * @throws ExchangeRateRequiredException      si falta el cambio de una moneda que no es
   *                                            la base
   * @throws ExchangeRateNotApplicableException si se informa un cambio para la moneda base
   */
  public static void validate(Currency currency, BigDecimal exchangeRate) {
    if (isBase(currency)) {
      if (exchangeRate != null) {
        throw new ExchangeRateNotApplicableException(
            Constants.EXCHANGE_RATE_NOT_APPLICABLE.replace("{}", currency.getValue()));
      }
      return;
    }
    if (exchangeRate == null) {
      throw new ExchangeRateRequiredException(
          Constants.EXCHANGE_RATE_REQUIRED.replace("{}", currency.getValue()));
    }
  }

  /**
   * Resuelve el tipo de referencia con el que se convierten los totales de un resumen.
   *
   * <p>A la moneda base no hace falta ninguno: cada movimiento se convierte con el suyo, el
   * que se guardó al registrarlo, y el de referencia vale uno. A otra moneda sí, porque un
   * movimiento en la base no tiene un cambio propio hacia ella; sin él, el total no se
   * podría calcular.</p>
   *
   * @param target moneda a la que se convierte
   * @param rate   tipo de referencia recibido, puede ser nulo
   * @return el tipo con el que dividir lo que se ha llevado a la base
   * @throws ExchangeRateRequiredException si se convierte a otra moneda sin tipo de
   *                                       referencia
   */
  public static BigDecimal conversionRate(Currency target, BigDecimal rate) {
    if (isBase(target)) {
      return BigDecimal.ONE;
    }
    if (rate == null) {
      throw new ExchangeRateRequiredException(
          Constants.CONVERSION_RATE_REQUIRED.replace("{}", target.getValue()));
    }
    return rate;
  }
}
