package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.query.AmountTotal;
import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.model.query.SummaryBucketSize;
import com.sreyes.finscope.model.query.TransactionSummaryCriteria;
import io.r2dbc.spi.Readable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repositorio de agregados sobre las transacciones.
 * Los totales se calculan en la base de datos y no recorriendo las transacciones en
 * memoria: el cliente necesita el saldo de un periodo completo, que puede abarcar miles de
 * movimientos, y traérselos para sumarlos obligaría a paginar hasta agotarlos.
 * Los filtros son los mismos que los del listado y se construyen dinámicamente, por lo que
 * la consulta se arma aquí en lugar de declararse; todo valor de la petición viaja como
 * parámetro enlazado y nunca concatenado.
 */
@Repository
@RequiredArgsConstructor
public class TransactionSummaryRepository {

  /**
   * Origen común de los cuatro agregados. El tipo se une siempre porque de su código
   * depende si el importe suma o resta.
   */
  private static final String FROM_TRANSACTIONS = """
      FROM transactions t
      INNER JOIN transaction_types tt ON tt.id_transaction_type = t.transaction_type_id
      """;

  /**
   * Unión con la categoría de cada transacción. Es interna porque la categoría es
   * obligatoria y única: cada transacción aporta exactamente una fila, ni más ni menos, y
   * eso es lo que hace que la suma por categoría coincida con el total del periodo.
   */
  private static final String JOIN_CATEGORIES = """
      INNER JOIN categories c ON c.id_category = t.category_id
      """;

  /**
   * Unión con los tags de cada transacción. Es externa a propósito: las transacciones sin
   * tag deben seguir contando, agrupadas bajo un nombre nulo.
   */
  private static final String JOIN_TAGS = """
      LEFT JOIN transaction_tags xt ON xt.transaction_id = t.id_transaction
      LEFT JOIN tags g ON g.id_tag = xt.tag_id
      """;

  private final DatabaseClient databaseClient;

  /**
   * Suma los importes del periodo agrupados por tipo de transacción.
   *
   * @param userId   identificador del usuario propietario
   * @param criteria filtros solicitados
   * @param range    rango de fechas ya resuelto
   * @return flujo con un total por cada tipo presente en el periodo
   */
  public Flux<AmountTotal> totalsByType(Long userId, TransactionSummaryCriteria criteria,
                                        DateRange range) {
    Conditions conditions = buildConditions(userId, criteria, range);
    String sql = """
        SELECT tt.code AS type_code,
               COALESCE(SUM(t.amount), 0) AS total,
               COUNT(*) AS movements
        """ + FROM_TRANSACTIONS + conditions.sql() + """
        GROUP BY tt.code
        """;
    return execute(sql, conditions, row -> toTotal(row, null, null, null, null));
  }

  /**
   * Suma los importes del periodo agrupados por categoría y por tipo de transacción.
   *
   * <p>Es el agregado que reparte el gasto. Como cada transacción tiene exactamente una
   * categoría, la unión no multiplica filas y ninguna se queda fuera, de modo que la suma
   * de todas las categorías coincide con el total del periodo y puede presentarse como
   * porcentajes. Es justo lo que no puede hacerse por tag.</p>
   *
   * @param userId   identificador del usuario propietario
   * @param criteria filtros solicitados
   * @param range    rango de fechas ya resuelto
   * @return flujo con un total por cada combinación de categoría y tipo
   */
  public Flux<AmountTotal> totalsByCategory(Long userId, TransactionSummaryCriteria criteria,
                                            DateRange range) {
    Conditions conditions = buildConditions(userId, criteria, range);
    String sql = """
        SELECT c.id_category AS category_id,
               c.name_category AS category_name,
               tt.code AS type_code,
               COALESCE(SUM(t.amount), 0) AS total,
               COUNT(*) AS movements
        """ + FROM_TRANSACTIONS + JOIN_CATEGORIES + conditions.sql() + """
        GROUP BY c.id_category, c.name_category, tt.code
        """;
    return execute(sql, conditions, row -> toTotal(row,
        row.get("category_id", Long.class), row.get("category_name", String.class), null, null));
  }

  /**
   * Suma los importes del periodo agrupados por tag y por tipo de transacción.
   * Una transacción con varios tags se cuenta entera en cada uno de ellos, porque el modelo
   * no define cómo repartir su importe. Las que no llevan ninguno se agrupan bajo un nombre
   * nulo en lugar de quedarse fuera.
   *
   * <p>Por eso este desglose no suma el total del periodo y no puede presentarse como un
   * reparto: para eso está {@link #totalsByCategory}.</p>
   *
   * @param userId   identificador del usuario propietario
   * @param criteria filtros solicitados
   * @param range    rango de fechas ya resuelto
   * @return flujo con un total por cada combinación de tag y tipo
   */
  public Flux<AmountTotal> totalsByTag(Long userId, TransactionSummaryCriteria criteria,
                                       DateRange range) {
    Conditions conditions = buildConditions(userId, criteria, range);
    String sql = """
        SELECT g.name_tag AS tag_name,
               tt.code AS type_code,
               COALESCE(SUM(t.amount), 0) AS total,
               COUNT(*) AS movements
        """ + FROM_TRANSACTIONS + JOIN_TAGS + conditions.sql() + """
        GROUP BY g.name_tag, tt.code
        """;
    return execute(sql, conditions,
        row -> toTotal(row, null, null, row.get("tag_name", String.class), null));
  }

  /**
   * Suma los importes del periodo agrupados por tramos de tiempo y por tipo de transacción.
   * Solo aparecen los tramos con alguna transacción: materializar los vacíos exigiría
   * generar la serie completa en la consulta, y el cliente puede completarlos con ceros
   * porque conoce el rango y el tamaño del tramo.
   *
   * @param userId     identificador del usuario propietario
   * @param criteria   filtros solicitados
   * @param range      rango de fechas ya resuelto
   * @param bucketSize tamaño de cada tramo
   * @return flujo con un total por cada combinación de tramo y tipo, en orden cronológico
   */
  public Flux<AmountTotal> totalsByPeriod(Long userId, TransactionSummaryCriteria criteria,
                                          DateRange range, SummaryBucketSize bucketSize) {
    Conditions conditions = buildConditions(userId, criteria, range);
    // La unidad sale de una enumeración del dominio, nunca de la petición.
    String sql = """
        SELECT DATE_TRUNC('%s', t.date) AS period_start,
               tt.code AS type_code,
               COALESCE(SUM(t.amount), 0) AS total,
               COUNT(*) AS movements
        """.formatted(bucketSize.sqlUnit()) + FROM_TRANSACTIONS + conditions.sql() + """
        GROUP BY period_start, tt.code
        ORDER BY period_start
        """;
    return execute(sql, conditions,
        row -> toTotal(row, null, null, null, row.get("period_start", LocalDateTime.class)));
  }

  /**
   * Lanza la consulta enlazando sus parámetros y proyectando cada fila.
   *
   * @param sql        consulta a ejecutar
   * @param conditions filtros aplicados, de donde salen los valores a enlazar
   * @param mapper     proyección de cada fila
   * @return flujo con los totales calculados
   */
  private Flux<AmountTotal> execute(String sql, Conditions conditions,
                                    Function<Readable, AmountTotal> mapper) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
    for (Map.Entry<String, Object> binding : conditions.bindings().entrySet()) {
      spec = spec.bind(binding.getKey(), binding.getValue());
    }
    return spec.map(mapper::apply).all();
  }

  /**
   * Proyecta una fila de agregado, tomando de ella lo que la consulta haya agrupado.
   *
   * @param row          fila devuelta por la base de datos
   * @param categoryId   identificador de la categoría del grupo, nulo si no se agrupa por
   *                     categoría
   * @param categoryName nombre de esa categoría, nulo si no se agrupa por categoría
   * @param tagName      tag del grupo, nulo si no se agrupa por tag
   * @param periodStart  inicio del tramo del grupo, nulo si no se agrupa por tiempo
   * @return el total representado por la fila
   */
  private AmountTotal toTotal(Readable row, Long categoryId, String categoryName, String tagName,
                              LocalDateTime periodStart) {
    BigDecimal total = row.get("total", BigDecimal.class);
    Long movements = row.get("movements", Long.class);
    return new AmountTotal(row.get("type_code", String.class),
        total == null ? BigDecimal.ZERO : total,
        movements == null ? 0L : movements,
        categoryId, categoryName, tagName, periodStart);
  }

  /**
   * Compone la cláusula de filtrado combinando con AND el usuario propietario, que siempre
   * está presente, y únicamente los filtros informados.
   * El filtro por tag se resuelve con una subconsulta de existencia en lugar de con una
   * unión, para que acotar por tag no multiplique las filas que se agregan.
   *
   * @param userId   identificador del usuario propietario
   * @param criteria filtros solicitados
   * @param range    rango de fechas ya resuelto
   * @return la cláusula construida junto a los valores que enlaza
   */
  private Conditions buildConditions(Long userId, TransactionSummaryCriteria criteria,
                                     DateRange range) {
    List<String> predicates = new ArrayList<>();
    Map<String, Object> bindings = new LinkedHashMap<>();

    predicates.add("t.user_id = :userId");
    bindings.put("userId", userId);

    if (range.from() != null) {
      predicates.add("t.date >= :dateFrom");
      bindings.put("dateFrom", range.from());
    }
    if (range.to() != null) {
      predicates.add("t.date <= :dateTo");
      bindings.put("dateTo", range.to());
    }
    if (criteria.transactionTypeId() != null) {
      predicates.add("t.transaction_type_id = :transactionTypeId");
      bindings.put("transactionTypeId", criteria.transactionTypeId());
    }
    if (criteria.categoryId() != null) {
      predicates.add("t.category_id = :categoryId");
      bindings.put("categoryId", criteria.categoryId());
    }
    if (criteria.tag() != null && !criteria.tag().isBlank()) {
      predicates.add("""
          EXISTS (SELECT 1
                  FROM transaction_tags ft
                  INNER JOIN tags fg ON fg.id_tag = ft.tag_id
                  WHERE ft.transaction_id = t.id_transaction
                    AND fg.user_id = :userId
                    AND LOWER(fg.name_tag) = LOWER(:tag))""");
      bindings.put("tag", criteria.tag().trim());
    }
    return new Conditions("WHERE " + String.join("\n  AND ", predicates) + "\n", bindings);
  }

  /**
   * Cláusula de filtrado de una consulta junto a los valores que enlaza.
   *
   * @param sql      texto de la cláusula, terminado en salto de línea
   * @param bindings valores a enlazar, indexados por el nombre del parámetro
   */
  private record Conditions(String sql, Map<String, Object> bindings) {
  }
}
