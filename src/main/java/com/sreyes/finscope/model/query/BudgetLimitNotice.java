package com.sreyes.finscope.model.query;

import java.math.BigDecimal;

/**
 * Un presupuesto del mes que ya llegó al umbral de aviso.
 *
 * @param userId       dueño del presupuesto
 * @param budgetId     identificador del presupuesto
 * @param categoryName categoría presupuestada
 * @param currency     moneda del plan y de lo gastado
 * @param amount       importe presupuestado
 * @param spent        lo gastado en el mes, con la misma cuenta que la barra de la pantalla
 */
public record BudgetLimitNotice(
    Long userId,
    Long budgetId,
    String categoryName,
    String currency,
    BigDecimal amount,
    BigDecimal spent) {
}
