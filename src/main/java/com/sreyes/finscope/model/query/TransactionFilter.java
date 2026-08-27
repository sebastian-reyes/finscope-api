package com.sreyes.finscope.model.query;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Criterios de búsqueda de transacciones ya normalizados por la capa de servicio.
 * Cada campo nulo representa un filtro no aplicado y todos se combinan con AND.
 *
 * @param userId            identificador del usuario propietario
 * @param dateFrom          fecha inicial inclusiva
 * @param dateTo            fecha final inclusiva
 * @param transactionTypeId identificador del tipo de transacción
 * @param categoryId        identificador de la categoría principal
 * @param transactionIds    restricción al conjunto de transacciones indicado, usada para
 *                          resolver el filtro por tag
 */
public record TransactionFilter(
    Long userId,
    LocalDateTime dateFrom,
    LocalDateTime dateTo,
    Long transactionTypeId,
    Long categoryId,
    List<Long> transactionIds) {
}
