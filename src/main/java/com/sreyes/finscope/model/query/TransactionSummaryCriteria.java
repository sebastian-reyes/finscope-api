package com.sreyes.finscope.model.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Filtros de un resumen de transacciones tal y como llegan desde la API.
 * Son los mismos que admite el listado salvo la paginación y el ordenamiento, que un
 * agregado no necesita: el resumen recorre todas las transacciones que cumplen los filtros,
 * no una página de ellas.
 *
 * @param month             mes de la transacción, entre 1 y 12
 * @param year              año de la transacción
 * @param dateFrom          fecha inicial inclusiva
 * @param dateTo            fecha final inclusiva
 * @param transactionTypeId identificador del tipo de transacción
 * @param categoryId        identificador de la categoría principal
 * @param tag               nombre del tag asociado, sin distinguir mayúsculas
 * @param search            texto a buscar en la descripción, la categoría y los tags, sin
 *                          distinguir mayúsculas
 * @param currency          moneda del movimiento, nula para no acotar por ella; es siempre una,
 *                          porque un total no puede sumar monedas distintas
 * @param convertTo         moneda a la que llevar todos los importes para sumarlos juntos, nula
 *                          para no convertir; con ella, {@code currency} viene nula
 * @param conversionRate    cuántas unidades de la base vale una de {@code convertTo}; es 1
 *                          cuando se convierte a la base, donde cada movimiento usa su propio
 *                          tipo de cambio, y nula cuando no se convierte
 */
public record TransactionSummaryCriteria(
    Integer month,
    Integer year,
    LocalDateTime dateFrom,
    LocalDateTime dateTo,
    Long transactionTypeId,
    Long categoryId,
    String tag,
    String search,
    String currency,
    String convertTo,
    BigDecimal conversionRate) {

  /**
   * Crea unos criterios que no convierten: cada total es de la moneda indicada.
   *
   * @param month             mes de la transacción, entre 1 y 12
   * @param year              año de la transacción
   * @param dateFrom          fecha inicial inclusiva
   * @param dateTo            fecha final inclusiva
   * @param transactionTypeId identificador del tipo de transacción
   * @param categoryId        identificador de la categoría principal
   * @param tag               nombre del tag asociado
   * @param search            texto a buscar
   * @param currency          moneda del movimiento, nula para no acotar por ella
   */
  public TransactionSummaryCriteria(Integer month, Integer year, LocalDateTime dateFrom,
                                    LocalDateTime dateTo, Long transactionTypeId,
                                    Long categoryId, String tag, String search, String currency) {
    this(month, year, dateFrom, dateTo, transactionTypeId, categoryId, tag, search, currency,
        null, null);
  }

  /**
   * Indica si los importes se suman convertidos a una sola moneda.
   *
   * @return si se ha pedido convertir
   */
  public boolean converted() {
    return convertTo != null;
  }

  /**
   * Devuelve estos mismos criterios sin acotar por moneda y sin convertir.
   *
   * <p>Lo necesita el único agregado que tiene que ver todas las monedas del periodo: el
   * que dice cuáles hay y cuánto suma cada una. Acotarlo por la moneda pedida lo dejaría
   * contestando siempre esa, que es justo lo que no se le está preguntando; y convertirlo
   * le quitaría lo único que aporta, que es cada moneda con lo que de verdad suma.</p>
   *
   * @return los criterios con el filtro de moneda y la conversión retirados
   */
  public TransactionSummaryCriteria withoutCurrency() {
    return new TransactionSummaryCriteria(month, year, dateFrom, dateTo, transactionTypeId,
        categoryId, tag, search, null, null, null);
  }
}
