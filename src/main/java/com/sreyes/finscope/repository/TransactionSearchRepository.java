package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.query.TransactionFilter;
import com.sreyes.finscope.util.query.LikePatterns;
import com.sreyes.finscope.util.query.TransactionSql;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.convert.R2dbcConverter;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio de consulta de transacciones con filtros dinámicos y paginación.
 * Los criterios opcionales de {@link TransactionFilter} no pueden expresarse mediante
 * métodos derivados, así que la consulta se arma aquí en lugar de declararse; todo valor de
 * la petición viaja como parámetro enlazado y nunca concatenado.
 *
 * <p>El filtro por texto mira también la categoría y los tags, y eso pide subconsultas de
 * existencia que los criterios de {@link R2dbcEntityTemplate} no saben expresar. De ahí que
 * la consulta se escriba a mano, igual que la de los agregados, con la que además comparte
 * el filtro para que la lista y sus totales no puedan acotar distinto. El mapeo de cada
 * fila sí sigue siendo el de la entidad, que es lo que evita tener que enumerar sus
 * columnas aquí.</p>
 */
@Repository
@RequiredArgsConstructor
public class TransactionSearchRepository {

  /** Origen común de la página y de su conteo. */
  private static final String FROM_TRANSACTIONS = """
      FROM transactions t
      """;

  /**
   * Columnas por las que se admite ordenar, asociadas al nombre de la propiedad de la
   * entidad. Actúa como lista blanca: el ordenamiento es lo único de la petición que acaba
   * en el texto de la consulta, así que solo puede salir de aquí.
   */
  private static final Map<String, String> SORT_COLUMNS =
      Map.of("date", "t.date", "amount", "t.amount", "id", "t.id_transaction");

  private final R2dbcEntityTemplate entityTemplate;

  /**
   * Obtiene la página de transacciones que cumplen los criterios indicados.
   *
   * @param filter   criterios de búsqueda ya normalizados
   * @param pageable página y ordenamiento solicitados
   * @return flujo reactivo con las transacciones de la página
   */
  public Flux<Transaction> search(TransactionFilter filter, Pageable pageable) {
    Conditions conditions = buildConditions(filter);
    String sql = "SELECT t.*\n" + FROM_TRANSACTIONS + conditions.sql()
        + orderBy(pageable.getSort()) + "LIMIT :limit OFFSET :offset\n";
    R2dbcConverter converter = entityTemplate.getConverter();
    return conditions.bind(entityTemplate.getDatabaseClient().sql(sql))
        .bind("limit", pageable.getPageSize())
        .bind("offset", pageable.getOffset())
        .map((row, metadata) -> converter.read(Transaction.class, row, metadata))
        .all();
  }

  /**
   * Cuenta el total de transacciones que cumplen los criterios indicados, sin paginar.
   *
   * @param filter criterios de búsqueda ya normalizados
   * @return cantidad total de transacciones coincidentes
   */
  public Mono<Long> count(TransactionFilter filter) {
    Conditions conditions = buildConditions(filter);
    String sql = "SELECT COUNT(*) AS total\n" + FROM_TRANSACTIONS + conditions.sql();
    return conditions.bind(entityTemplate.getDatabaseClient().sql(sql))
        .map(row -> row.get("total", Long.class))
        .one()
        .defaultIfEmpty(0L);
  }

  /**
   * Compone la cláusula de filtrado combinando con AND el usuario propietario, que siempre
   * está presente, y únicamente los filtros informados.
   *
   * @param filter criterios de búsqueda ya normalizados
   * @return la cláusula construida junto a los valores que enlaza
   */
  private Conditions buildConditions(TransactionFilter filter) {
    Conditions conditions = new Conditions()
        .add("t.user_id = :userId", "userId", filter.userId());
    if (filter.dateFrom() != null) {
      conditions.add("t.date >= :dateFrom", "dateFrom", filter.dateFrom());
    }
    if (filter.dateTo() != null) {
      conditions.add("t.date <= :dateTo", "dateTo", filter.dateTo());
    }
    if (filter.transactionTypeId() != null) {
      conditions.add("t.transaction_type_id = :transactionTypeId", "transactionTypeId",
          filter.transactionTypeId());
    }
    if (filter.categoryId() != null) {
      conditions.add("t.category_id = :categoryId", "categoryId", filter.categoryId());
    }
    if (filter.currency() != null) {
      conditions.add("t.currency = :currency", "currency", filter.currency());
    }
    if (filter.search() != null) {
      conditions.add(TransactionSql.MATCHES_TEXT, "search",
          LikePatterns.contains(filter.search()));
    }
    if (filter.transactionIds() != null) {
      // Se enlaza como un arreglo y se compara con ANY en lugar de expandir un IN con un
      // parámetro por identificador: la lista la produce el filtro por tag y crece con lo
      // usado que esté, de modo que un tag frecuente generaría una consulta distinta cada
      // vez y ninguna reaprovechable.
      conditions.add("t.id_transaction = ANY(:transactionIds)", "transactionIds",
          filter.transactionIds().toArray(Long[]::new));
    }
    return conditions;
  }

  /**
   * Traduce el ordenamiento solicitado a su cláusula, tomando cada columna de la lista
   * blanca. Un campo que no esté en ella se ignora en lugar de llegar a la consulta.
   *
   * @param sort ordenamiento solicitado
   * @return el texto de la cláusula, terminado en salto de línea
   */
  private String orderBy(Sort sort) {
    String clauses = sort.stream()
        .filter(order -> SORT_COLUMNS.containsKey(order.getProperty()))
        .map(order -> SORT_COLUMNS.get(order.getProperty()) + " " + order.getDirection().name())
        .reduce((left, right) -> left + ", " + right)
        .orElse("t.date DESC");
    return "ORDER BY " + clauses + "\n";
  }

  /**
   * Ordenamiento por defecto aplicado cuando la petición no indica uno.
   *
   * @return ordenamiento descendente por fecha
   */
  public static Sort defaultSort() {
    return Sort.by(Sort.Direction.DESC, "date");
  }
}
