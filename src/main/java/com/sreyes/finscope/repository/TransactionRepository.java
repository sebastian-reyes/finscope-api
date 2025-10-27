package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Transaction;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repositorio para la entidad {@link Transaction}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `transactions`.
 * Extiende {@link R2dbcRepository} para soporte CRUD.
 * Incluye métodos personalizados para consultar transacciones por tipo, categoría y fecha.
 */
@Repository
public interface TransactionRepository extends R2dbcRepository<Transaction, Long> {

  /**
   * Obtiene todas las transacciones por el identificador de tipo de transacción.
   *
   * @param id identificador del tipo de transacción
   * @return flujo reactivo de transacciones
   */
  @Query("SELECT * FROM transactions WHERE transaction_type_id = :id")
  Flux<Transaction> findByTransactionTypeId(Long id);

  /**
   * Obtiene todas las transacciones por el identificador de categoría.
   *
   * @param categoryId identificador de la categoría
   * @return flujo reactivo de transacciones
   */
  @Query("SELECT * FROM transactions WHERE category_id = :id")
  Flux<Transaction> findByCategoryId(Long categoryId);

  /**
   * Obtiene todas las transacciones por mes y año.
   *
   * @param year año de la transacción
   * @param month mes de la transacción
   * @return flujo reactivo de transacciones
   */
  @Query("""
      SELECT * FROM transactions
      WHERE EXTRACT(YEAR FROM date) = :year AND EXTRACT(MONTH FROM date) = :month
      """)
  Flux<Transaction> findByMonthAndYear(int year, int month);

}

