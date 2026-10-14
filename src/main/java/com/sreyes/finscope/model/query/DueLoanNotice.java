package com.sreyes.finscope.model.query;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una cuota de un préstamo que todavía no está cubierta y de la que hay que avisar.
 *
 * @param userId           quien prestó el dinero
 * @param loanId           identificador del préstamo
 * @param installmentId    identificador de la cuota, que es lo que se apunta como avisado
 * @param borrowerName     a quién se le prestó, que es lo que se reconoce en el aviso
 * @param currency         moneda del préstamo
 * @param number           orden de la cuota en el plan
 * @param installmentCount cuántas cuotas tiene el plan; con una sola no se habla de cuotas
 * @param dueDate          día en que vence la cuota
 * @param pending          lo que falta de esa cuota, que puede ser menos que su importe
 */
public record DueLoanNotice(
    Long userId,
    Long loanId,
    Long installmentId,
    String borrowerName,
    String currency,
    Integer number,
    Integer installmentCount,
    LocalDate dueDate,
    BigDecimal pending) {
}
