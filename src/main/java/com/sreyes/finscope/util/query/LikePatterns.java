package com.sreyes.finscope.util.query;

import lombok.experimental.UtilityClass;

/**
 * Construcción de los patrones de las comparaciones LIKE a partir de texto escrito por el
 * usuario.
 * El texto de una búsqueda es texto y no un patrón: quien escribe «50%» busca esa cadena y
 * no «cualquier cosa que empiece por 50». Dejar pasar los comodines convertiría cada
 * búsqueda con un porcentaje o un guion bajo en un resultado inexplicable, así que se
 * escapan antes de envolver el término.
 * Esta clase no debe ser instanciada.
 */
@UtilityClass
public final class LikePatterns {

  /**
   * Carácter de escape que PostgreSQL asume en un LIKE sin cláusula ESCAPE, de modo que el
   * patrón funciona sin tener que declararlo en cada consulta.
   */
  private static final char ESCAPE = '\\';

  /**
   * Envuelve el término en un patrón que casa con cualquier texto que lo contenga.
   *
   * @param term texto buscado, tal y como lo escribió el usuario
   * @return el patrón LIKE correspondiente, con los comodines del término neutralizados
   */
  public static String contains(String term) {
    StringBuilder pattern = new StringBuilder(term.length() + 2).append('%');
    for (char character : term.toCharArray()) {
      if (character == ESCAPE || character == '%' || character == '_') {
        pattern.append(ESCAPE);
      }
      pattern.append(character);
    }
    return pattern.append('%').toString();
  }
}
