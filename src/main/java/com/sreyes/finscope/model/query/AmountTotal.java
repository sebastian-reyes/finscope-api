package com.sreyes.finscope.model.query;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Importe acumulado por un grupo de transacciones dentro de un resumen.
 * Los cuatro agregados que calcula la base de datos, el total del periodo, el total por
 * categoría, el total por tag y el total por tramo de tiempo, comparten forma: suman importes
 * de un mismo tipo de transacción y cuentan cuántas los componen. Cambia solo por qué se
 * agrupa, y eso es lo que distinguen {@code categoryId}, {@code tagName} y
 * {@code periodStart}, nulos cuando no se agrupa por ellos.
 *
 * @param typeCode     código del tipo de transacción, INCOME o EXPENSE
 * @param total        suma de los importes del grupo, siempre positiva
 * @param movements    cuántas transacciones componen el grupo
 * @param categoryId   identificador de la categoría por la que se agrupa, nulo si no se
 *                     agrupa por categoría
 * @param categoryName nombre de esa categoría, nulo si no se agrupa por categoría
 * @param tagName      tag por el que se agrupa; nulo al no agrupar por tag y también en el
 *                     grupo de las transacciones que no llevan ninguno
 * @param periodStart  instante inicial del tramo por el que se agrupa, nulo si no se agrupa
 *                     por tiempo
 */
public record AmountTotal(
    String typeCode,
    BigDecimal total,
    long movements,
    Long categoryId,
    String categoryName,
    String tagName,
    LocalDateTime periodStart) {
}
