package com.sreyes.finscope.model.query;

import java.math.BigDecimal;

/**
 * Un movimiento fijo pendiente que vence en una fecha concreta y del que hay que avisar.
 *
 * @param userId      dueño del fijo
 * @param recurringId identificador de la plantilla
 * @param description descripción, que es lo que se reconoce en el aviso («Alquiler»)
 * @param amount      importe previsto
 * @param currency    moneda del importe
 * @param typeCode    EXPENSE o INCOME, que cambia la frase: un pago vence, un cobro toca
 */
public record DueRecurringNotice(
    Long userId,
    Long recurringId,
    String description,
    BigDecimal amount,
    String currency,
    String typeCode) {
}
