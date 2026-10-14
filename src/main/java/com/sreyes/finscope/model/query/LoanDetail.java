package com.sreyes.finscope.model.query;

import com.sreyes.finscope.api.model.LoanStatus;
import com.sreyes.finscope.model.entity.Loan;
import com.sreyes.finscope.model.entity.LoanPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un préstamo resuelto contra el día de hoy: su plan, lo devuelto y lo que falta.
 *
 * <p>Préstamo y cifras viajan juntos porque por separado no sirven de nada: saber que se
 * prestaron mil sin saber cuánto volvió no ayuda a cobrar.</p>
 *
 * @param loan          el préstamo tal y como está guardado
 * @param total         capital más interés, que es lo que hay que devolver
 * @param paid          lo devuelto hasta hoy
 * @param outstanding   lo que falta, nunca negativo
 * @param overdueAmount parte de lo que falta cuya cuota ya venció
 * @param status        estado del préstamo hoy
 * @param nextDueDate   fecha de la primera cuota sin cubrir del todo, o nula si no queda
 * @param nextDueAmount lo que falta de esa cuota, o nulo si no queda
 * @param installments  plan de devolución en orden de cuota
 * @param payments      lo devuelto, del pago más reciente al más antiguo
 */
public record LoanDetail(
    Loan loan,
    BigDecimal total,
    BigDecimal paid,
    BigDecimal outstanding,
    BigDecimal overdueAmount,
    LoanStatus status,
    LocalDate nextDueDate,
    BigDecimal nextDueAmount,
    List<LoanInstallmentDetail> installments,
    List<LoanPayment> payments) {
}
