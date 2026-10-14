package com.sreyes.finscope.model.query;

import com.sreyes.finscope.api.model.LoanInstallmentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una cuota del plan junto a lo que ya la cubre.
 *
 * @param id      identificador de la cuota
 * @param number  orden de la cuota en el plan, desde 1
 * @param dueDate día en que vence
 * @param amount  lo que se acordó devolver en ella
 * @param paid    parte cubierta por lo devuelto, entre cero y el importe
 * @param status  estado de la cuota hoy
 */
public record LoanInstallmentDetail(
    Long id,
    Integer number,
    LocalDate dueDate,
    BigDecimal amount,
    BigDecimal paid,
    LoanInstallmentStatus status) {
}
