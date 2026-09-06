package com.sreyes.finscope.util.query;

import lombok.experimental.UtilityClass;

/**
 * Fragmentos de SQL de las transacciones que necesita más de una consulta.
 * Esta clase no debe ser instanciada.
 *
 * Vive aparte porque el listado y el resumen tienen que entender lo mismo por «buscar»:
 * sobre la lista se enseñan los totales de esos mismos filtros, así que en cuanto una de
 * las dos consultas mirase en un sitio más que la otra, la cifra de arriba dejaría de ser
 * la suma de lo que hay debajo y no habría forma de saber cuál de las dos miente.
 */
@UtilityClass
public final class TransactionSql {

  /**
   * Condición que decide si la transacción `t` responde al texto buscado en `:search`.
   *
   * <p>Se mira en los tres sitios donde el usuario pudo dejar esa palabra: la descripción
   * que escribió, el nombre de la categoría en la que la puso y el de cualquiera de sus
   * tags. Quien busca «dentista» no se acuerda de en cuál de los tres lo apuntó, y esa es
   * justo la pregunta que el cajón viene a contestar.</p>
   *
   * <p>Los tags y la categoría se resuelven con subconsultas de existencia y no con
   * uniones: una unión multiplicaría la transacción por cada tag que casara y los importes
   * se contarían dos veces. Con EXISTS cada transacción entra una vez o ninguna.</p>
   *
   * <p>El parámetro `:search` llega como un patrón ya montado y con sus comodines
   * neutralizados; de eso se encarga {@link LikePatterns}. Va entre paréntesis y sin salto
   * final para poder encadenarse con AND al resto de filtros.</p>
   */
  public static final String MATCHES_TEXT = """
      (LOWER(t.description) LIKE LOWER(:search)
         OR EXISTS (SELECT 1
                    FROM categories sc
                    WHERE sc.id_category = t.category_id
                      AND LOWER(sc.name_category) LIKE LOWER(:search))
         OR EXISTS (SELECT 1
                    FROM transaction_tags st
                    INNER JOIN tags sg ON sg.id_tag = st.tag_id
                    WHERE st.transaction_id = t.id_transaction
                      AND LOWER(sg.name_tag) LIKE LOWER(:search)))\
      """;
}
