package com.sreyes.finscope.util.query;

import lombok.experimental.UtilityClass;

/**
 * Fragmentos de SQL de los movimientos fijos que necesita más de una consulta.
 * Esta clase no debe ser instanciada.
 *
 * Vive aparte porque la regla de cuándo vence un fijo la usan dos sitios distintos: el
 * listado de la pantalla de fijos y el cálculo de lo comprometido que acompaña a cada
 * presupuesto. Si cada uno tuviera su copia y una de las dos se quedara atrás, la lista
 * diría que el internet vence este mes y la barra del presupuesto no lo estaría contando,
 * o al revés, y no habría forma de saber cuál de las dos miente.
 */
@UtilityClass
public final class RecurringSql {

  /**
   * Condición que decide si la plantilla `r` vence en el mes `:month` de `:year`.
   *
   * <p>Se cuenta la distancia en meses desde el arranque y se mira si es múltiplo del
   * periodo: que no sea negativa descarta los meses anteriores al alta, y el módulo
   * descarta los meses intermedios de un fijo bimestral o anual. Una plantilla pausada no
   * vence nunca, y por eso `active` entra en la misma condición y no aparte.</p>
   *
   * <p>Va entre paréntesis y sin salto final para poder usarse tanto en un WHERE como en
   * la lista de selección, que es justo lo que evita tener dos versiones de la regla.</p>
   */
  public static final String DUE_IN_PERIOD = """
      (r.active
         AND (:year - r.start_year) * 12 + (:month - r.start_month) >= 0
         AND MOD((:year - r.start_year) * 12 + (:month - r.start_month), r.every_months) = 0)\
      """;
}
