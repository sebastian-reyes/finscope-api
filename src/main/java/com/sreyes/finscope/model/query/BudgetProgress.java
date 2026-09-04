package com.sreyes.finscope.model.query;

import java.math.BigDecimal;

/**
 * Presupuesto de una categoría junto al gasto que lleva acumulado en ese mes.
 *
 * El plan y la realidad se resuelven en la misma consulta porque por separado no dicen
 * nada: un límite de 400 solo significa algo al lado de los 340 que ya se fueron. Traerlos
 * en dos viajes obligaría además a correlacionarlos en el cliente por categoría.
 *
 * Los nombres de las propiedades evitan a propósito llamarse `id`, `categoryId`, `month`,
 * `year` o `amount`, por el mismo motivo que {@link CategoryUsage}: coincidirían con las
 * propiedades homónimas de {@link com.sreyes.finscope.model.entity.Budget} y el conversor
 * iría a buscar las columnas de la entidad en lugar de los alias de la consulta, dejando
 * toda la proyección a nulo sin dar ningún error.
 *
 * @param budgetId           identificador del presupuesto
 * @param budgetCategoryId   identificador de la categoría presupuestada
 * @param budgetCategoryName nombre de esa categoría, con la grafía que escribió el usuario
 * @param budgetMonth        mes al que se aplica, entre 1 y 12
 * @param budgetYear         año al que se aplica
 * @param budgetAmount       importe presupuestado
 * @param budgetSpent        egresos de la categoría dentro del mes, cero si no hubo ninguno
 * @param budgetCommitted    importe de los movimientos fijos de la categoría que vencen ese
 *                           mes y todavía no se han confirmado, cero si no hay ninguno
 */
public record BudgetProgress(
    Long budgetId,
    Long budgetCategoryId,
    String budgetCategoryName,
    Integer budgetMonth,
    Integer budgetYear,
    BigDecimal budgetAmount,
    BigDecimal budgetSpent,
    BigDecimal budgetCommitted) {
}
