package com.sreyes.finscope.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Cláusula de filtrado de una consulta junto a los valores que enlaza.
 *
 * <p>La comparten el listado de transacciones y sus agregados porque construyen el mismo
 * filtro: sobre la lista se enseñan los totales de esos mismos criterios, y en cuanto una
 * de las dos consultas acotara distinto, la cifra de arriba dejaría de ser la suma de lo
 * que hay debajo.</p>
 *
 * <p>Se construye acumulando predicados con sus valores, de modo que ningún dato de la
 * petición llegue a concatenarse en el texto de la consulta.</p>
 */
final class Conditions {

  private final List<String> predicates = new ArrayList<>();
  private final Map<String, Object> bindings = new LinkedHashMap<>();

  /**
   * Añade un predicado que no enlaza ningún valor.
   *
   * @param predicate texto del predicado
   * @return estas mismas condiciones, para poder encadenar
   */
  Conditions add(String predicate) {
    predicates.add(predicate);
    return this;
  }

  /**
   * Añade un predicado junto al valor que enlaza.
   *
   * @param predicate texto del predicado, que nombra el parámetro
   * @param name      nombre del parámetro
   * @param value     valor a enlazar
   * @return estas mismas condiciones, para poder encadenar
   */
  Conditions add(String predicate, String name, Object value) {
    bindings.put(name, value);
    return add(predicate);
  }

  /**
   * Enlaza un valor que no filtra, sino que usa otra parte de la consulta, como la
   * conversión de los importes de un resumen.
   *
   * @param name  nombre del parámetro
   * @param value valor a enlazar
   * @return estas mismas condiciones, para poder encadenar
   */
  Conditions with(String name, Object value) {
    bindings.put(name, value);
    return this;
  }

  /**
   * Compone la cláusula WHERE combinando con AND todos los predicados acumulados.
   *
   * @return el texto de la cláusula, terminado en salto de línea
   */
  String sql() {
    return "WHERE " + String.join("\n  AND ", predicates) + "\n";
  }

  /**
   * Enlaza sobre la consulta los valores acumulados.
   *
   * @param spec consulta a la que enlazarlos
   * @return la consulta con sus parámetros ya enlazados
   */
  DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec) {
    DatabaseClient.GenericExecuteSpec bound = spec;
    for (Map.Entry<String, Object> binding : bindings.entrySet()) {
      bound = bound.bind(binding.getKey(), binding.getValue());
    }
    return bound;
  }
}
