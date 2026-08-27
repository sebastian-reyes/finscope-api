package com.sreyes.finscope.model.query;

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
 */
public record TransactionSummaryCriteria(
    Integer month,
    Integer year,
    LocalDateTime dateFrom,
    LocalDateTime dateTo,
    Long transactionTypeId,
    Long categoryId,
    String tag) {
}
