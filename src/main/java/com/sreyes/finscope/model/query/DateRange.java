package com.sreyes.finscope.model.query;

import java.time.LocalDateTime;

/**
 * Rango de fechas efectivo de una consulta, con ambos extremos inclusivos.
 * Un extremo nulo significa que por ese lado no se acota.
 *
 * @param from fecha inicial, nula si no se filtra por fecha
 * @param to   fecha final, nula si no se filtra por fecha
 */
public record DateRange(LocalDateTime from, LocalDateTime to) {

  /**
   * Rango sin acotar por ninguno de sus extremos.
   *
   * @return el rango que no descarta ninguna fecha
   */
  public static DateRange unbounded() {
    return new DateRange(null, null);
  }
}
