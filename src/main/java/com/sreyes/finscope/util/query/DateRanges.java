package com.sreyes.finscope.util.query;

import com.sreyes.finscope.exception.custom.DateNotFoundException;
import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.util.constants.Constants;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import lombok.experimental.UtilityClass;

/**
 * Resolución del rango de fechas que aplica a una consulta de transacciones.
 * Vive aparte de los servicios porque el listado y los resúmenes admiten exactamente los
 * mismos filtros de fecha y deben interpretarlos igual: si divergieran, un resumen dejaría
 * de corresponder con el listado que lo acompaña.
 * Esta clase no debe ser instanciada.
 */
@UtilityClass
public final class DateRanges {

  /**
   * Resuelve el rango de fechas efectivo a partir de los filtros recibidos.
   * Los filtros de mes y año son un atajo para acotar a un mes natural completo y no pueden
   * combinarse con un rango explícito de fechas.
   *
   * @param month    mes solicitado, nulo si no se filtra por mes
   * @param year     año solicitado, nulo si no se filtra por año
   * @param dateFrom fecha inicial inclusiva, nula si no se acota
   * @param dateTo   fecha final inclusiva, nula si no se acota
   * @return el rango de fechas aplicable, sin acotar si no se filtra por fecha
   */
  public static DateRange resolve(Integer month, Integer year, LocalDateTime dateFrom,
                                  LocalDateTime dateTo) {
    boolean hasMonthFilter = month != null || year != null;
    boolean hasRangeFilter = dateFrom != null || dateTo != null;

    if (hasMonthFilter && hasRangeFilter) {
      throw new DateNotFoundException(Constants.CONFLICTING_DATE_FILTERS);
    }
    if (hasMonthFilter) {
      return resolveMonthRange(month, year);
    }
    if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
      throw new DateNotFoundException(Constants.INVALID_DATE_RANGE);
    }
    return new DateRange(dateFrom, dateTo);
  }

  /**
   * Convierte el filtro de mes y año en el rango de fechas que abarca ese mes completo.
   *
   * @param month mes solicitado
   * @param year  año solicitado
   * @return el rango de fechas correspondiente al mes indicado
   */
  private static DateRange resolveMonthRange(Integer month, Integer year) {
    if (month == null || year == null) {
      throw new DateNotFoundException(Constants.INCOMPLETE_MONTH_FILTER);
    }
    if (month < 1 || month > 12) {
      throw new DateNotFoundException(Constants.INVALID_MONTH);
    }
    YearMonth yearMonth = YearMonth.of(year, month);
    return new DateRange(yearMonth.atDay(1).atStartOfDay(),
        yearMonth.atEndOfMonth().atTime(LocalTime.MAX));
  }
}
