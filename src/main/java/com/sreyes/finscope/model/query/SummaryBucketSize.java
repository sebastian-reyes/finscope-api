package com.sreyes.finscope.model.query;

/**
 * Tamaño de cada tramo al agrupar transacciones en una serie temporal.
 * Duplica los valores de la granularidad del contrato a propósito: la unidad viaja hasta la
 * consulta SQL, así que debe salir de un catálogo cerrado del propio dominio y no de un
 * texto de la petición. Traducir el valor de la API a esta enumeración es lo que garantiza
 * que nunca se interpole en la consulta algo que el usuario haya escrito.
 */
public enum SummaryBucketSize {

  DAY("day"),
  WEEK("week"),
  MONTH("month");

  private final String sqlUnit;

  SummaryBucketSize(String sqlUnit) {
    this.sqlUnit = sqlUnit;
  }

  /**
   * Unidad que entiende {@code date_trunc} para recortar la fecha al inicio del tramo.
   *
   * @return la unidad de truncado
   */
  public String sqlUnit() {
    return sqlUnit;
  }
}
