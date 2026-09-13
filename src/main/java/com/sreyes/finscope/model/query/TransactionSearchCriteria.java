package com.sreyes.finscope.model.query;

import java.time.LocalDateTime;

/**
 * Criterios de búsqueda de transacciones tal y como llegan desde la API, antes de ser
 * normalizados por la capa de servicio.
 * Agrupa los filtros, la paginación y el ordenamiento para no propagar una lista larga de
 * parámetros entre capas.
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
 * @param currency          moneda del movimiento, nula para no acotar por ella
 * @param page              número de página, empezando en cero
 * @param size              cantidad de elementos por página
 * @param sort              criterio de ordenamiento con formato campo,direccion
 */
public record TransactionSearchCriteria(
    Integer month,
    Integer year,
    LocalDateTime dateFrom,
    LocalDateTime dateTo,
    Long transactionTypeId,
    Long categoryId,
    String tag,
    String search,
    String currency,
    Integer page,
    Integer size,
    String sort) {
}
