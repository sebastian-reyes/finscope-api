package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.query.TransactionFilter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio de consulta de transacciones con filtros dinámicos y paginación.
 * Los criterios opcionales de {@link TransactionFilter} no pueden expresarse mediante
 * métodos derivados, por lo que se construyen con {@link Criteria} sobre
 * {@link R2dbcEntityTemplate}, que además evita concatenar SQL manualmente.
 */
@Repository
@RequiredArgsConstructor
public class TransactionSearchRepository {

  private final R2dbcEntityTemplate entityTemplate;

  /**
   * Obtiene la página de transacciones que cumplen los criterios indicados.
   *
   * @param filter   criterios de búsqueda ya normalizados
   * @param pageable página y ordenamiento solicitados
   * @return flujo reactivo con las transacciones de la página
   */
  public Flux<Transaction> search(TransactionFilter filter, Pageable pageable) {
    Query query = Query.query(buildCriteria(filter))
        .sort(pageable.getSort())
        .limit(pageable.getPageSize())
        .offset(pageable.getOffset());
    return entityTemplate.select(Transaction.class).matching(query).all();
  }

  /**
   * Cuenta el total de transacciones que cumplen los criterios indicados, sin paginar.
   *
   * @param filter criterios de búsqueda ya normalizados
   * @return cantidad total de transacciones coincidentes
   */
  public Mono<Long> count(TransactionFilter filter) {
    return entityTemplate.select(Transaction.class)
        .matching(Query.query(buildCriteria(filter)))
        .count();
  }

  /**
   * Compone los criterios aplicables combinando con AND el usuario propietario, que
   * siempre está presente, y únicamente los filtros informados.
   *
   * @param filter criterios de búsqueda ya normalizados
   * @return criterio combinado, que siempre acota al usuario propietario
   */
  private Criteria buildCriteria(TransactionFilter filter) {
    List<Criteria> criteria = new ArrayList<>();
    criteria.add(Criteria.where("userId").is(filter.userId()));
    if (filter.dateFrom() != null) {
      criteria.add(Criteria.where("date").greaterThanOrEquals(filter.dateFrom()));
    }
    if (filter.dateTo() != null) {
      criteria.add(Criteria.where("date").lessThanOrEquals(filter.dateTo()));
    }
    if (filter.transactionTypeId() != null) {
      criteria.add(Criteria.where("transactionTypeId").is(filter.transactionTypeId()));
    }
    if (filter.transactionIds() != null) {
      criteria.add(Criteria.where("id").in(filter.transactionIds()));
    }
    return criteria.stream().reduce(Criteria.empty(), Criteria::and);
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
