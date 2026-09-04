package com.sreyes.finscope.model.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento fijo tal y como lo devuelve la consulta: la plantilla, el nombre de su
 * categoría, el código de su tipo y lo que la base sabe de un mes concreto, que es si está
 * omitido y con qué movimiento se confirmó.
 *
 * Lo que no está aquí es el estado. La base dice los hechos —hay o no hay movimiento
 * enlazado, hay o no hay omisión— y el estado se decide después, en el servicio, porque
 * distinguir un pendiente de un vencido necesita saber qué día es hoy y eso no es asunto
 * de una consulta.
 *
 * Los nombres de las propiedades evitan a propósito llamarse `id`, `categoryId`, `amount`
 * o `active`: coincidirían con las propiedades homónimas de
 * {@link com.sreyes.finscope.model.entity.RecurringTransaction} y el conversor iría a
 * buscar las columnas de la entidad en lugar de los alias de la consulta, dejando toda la
 * proyección a nulo sin dar ningún error.
 *
 * @param recurringId            identificador de la plantilla
 * @param recurringCategoryId    identificador de la categoría del movimiento
 * @param recurringCategoryName  nombre de esa categoría, con la grafía que escribió el usuario
 * @param recurringTypeId        identificador del tipo de movimiento
 * @param recurringTypeCode      código del tipo, INCOME o EXPENSE
 * @param recurringDescription   cómo llama el usuario a este fijo
 * @param recurringAmount        importe estimado de la plantilla
 * @param recurringDayOfMonth    día previsto dentro del mes, sin recortar
 * @param recurringEveryMonths   cada cuántos meses toca
 * @param recurringStartMonth    mes desde el que aplica
 * @param recurringStartYear     año desde el que aplica
 * @param recurringActive        si la plantilla está activa o pausada
 * @param recurringMonth         mes contra el que se resolvió, tal y como se pidió
 * @param recurringYear          año contra el que se resolvió
 * @param recurringDue           si la plantilla vence en ese mes: activa, ya arrancada y
 *                               en uno de los meses que le tocan
 * @param recurringSkipped       si el usuario omitió ese mes
 * @param recurringTransactionId movimiento con el que se confirmó ese mes, nulo si no lo está
 * @param recurringPaidAmount    importe de ese movimiento, que puede no ser el estimado
 * @param recurringPaidDate      fecha de ese movimiento
 */
public record RecurringDetail(
    Long recurringId,
    Long recurringCategoryId,
    String recurringCategoryName,
    Long recurringTypeId,
    String recurringTypeCode,
    String recurringDescription,
    BigDecimal recurringAmount,
    Integer recurringDayOfMonth,
    Integer recurringEveryMonths,
    Integer recurringStartMonth,
    Integer recurringStartYear,
    Boolean recurringActive,
    Integer recurringMonth,
    Integer recurringYear,
    Boolean recurringDue,
    Boolean recurringSkipped,
    Long recurringTransactionId,
    BigDecimal recurringPaidAmount,
    LocalDateTime recurringPaidDate) {
}
