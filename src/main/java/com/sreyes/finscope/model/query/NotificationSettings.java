package com.sreyes.finscope.model.query;

/**
 * Qué avisos quiere recibir un usuario en sus dispositivos.
 *
 * @param recurringDue si se avisa de los movimientos fijos que vencen
 * @param budgetLimit  si se avisa de los presupuestos que se acercan a su límite o lo pasan
 */
public record NotificationSettings(boolean recurringDue, boolean budgetLimit) {

  /** Lo que tiene quien nunca ha tocado sus preferencias: todo encendido. */
  public static final NotificationSettings DEFAULTS = new NotificationSettings(true, true);
}
