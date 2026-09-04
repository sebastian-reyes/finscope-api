package com.sreyes.finscope.model.query;

import java.time.LocalDate;

/**
 * Un movimiento fijo resuelto contra un mes: la plantilla, el día en que vence en ese mes y
 * el estado en que está.
 *
 * Plantilla y estado viajan juntos porque por separado no sirven de nada: una lista de
 * fijos sin saber cuáles faltan no es un checklist, es un catálogo.
 *
 * @param recurring lo que la consulta sabe de la plantilla y de ese mes
 * @param dueDate   día concreto en que vence, con el día ya recortado a la longitud del
 *                  mes; nulo cuando no vence en ese mes
 * @param state     estado resuelto para ese mes
 */
public record RecurringOccurrence(
    RecurringDetail recurring,
    LocalDate dueDate,
    RecurringState state) {
}
