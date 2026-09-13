package com.sreyes.finscope.model.query;

import java.time.LocalDate;
import java.util.List;

/**
 * Un movimiento fijo resuelto contra un mes: la plantilla, sus tags, el día en que vence en
 * ese mes y el estado en que está.
 *
 * Plantilla y estado viajan juntos porque por separado no sirven de nada: una lista de
 * fijos sin saber cuáles faltan no es un checklist, es un catálogo.
 *
 * Los tags van aparte de la proyección porque viven en su propia tabla de enlace y se
 * cargan en lote para toda la lista, igual que los de una página de transacciones: traerlos
 * dentro de la consulta obligaría a agregar por fila y a repetir el resto de columnas por
 * cada tag.
 *
 * @param recurring lo que la consulta sabe de la plantilla y de ese mes
 * @param dueDate   día concreto en que vence, con el día ya recortado a la longitud del
 *                  mes; nulo cuando no vence en ese mes
 * @param state     estado resuelto para ese mes
 * @param tags      tags de la plantilla, en orden alfabético; vacío si no lleva ninguno
 */
public record RecurringOccurrence(
    RecurringDetail recurring,
    LocalDate dueDate,
    RecurringState state,
    List<String> tags) {
}
