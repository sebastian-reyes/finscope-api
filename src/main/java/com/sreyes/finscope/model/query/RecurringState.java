package com.sreyes.finscope.model.query;

/**
 * Estado de un movimiento fijo dentro de un mes concreto.
 *
 * No se guarda en ninguna parte: se decide al leer, a partir de si el mes está omitido, de
 * si hay un movimiento enlazado en él y de qué día es hoy. Guardarlo obligaría a repasar
 * todas las plantillas cada vez que se registra o se borra un movimiento, y bastaría con
 * que fallara una de esas veces para que la lista mintiera sin que nadie se enterase.
 */
public enum RecurringState {

  /**
   * Toca este mes y todavía no se ha registrado, pero aún no ha llegado su día.
   */
  PENDING,

  /**
   * Toca, no se ha registrado y su día ya pasó. Es el único estado que necesita saber la
   * fecha de hoy, y el único que la pantalla pinta en rojo.
   */
  OVERDUE,

  /**
   * Ya se confirmó: hay un movimiento real enlazado a la plantilla dentro del mes.
   */
  PAID,

  /**
   * El usuario decidió que este mes no toca. No vence ni compromete presupuesto.
   */
  SKIPPED,

  /**
   * No vence en ese mes: o la plantilla está pausada, o toca cada varios meses y ese no es
   * uno de ellos. Para quien mira la lista los dos casos son el mismo; el campo `active`
   * distingue cuál de ellos es.
   */
  NOT_DUE
}
