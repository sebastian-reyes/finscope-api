package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Transaction;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link Transaction}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `transactions`.
 * Extiende {@link R2dbcRepository} para soporte CRUD.
 * Las consultas filtradas y paginadas se resuelven en {@link TransactionSearchRepository}.
 */
@Repository
public interface TransactionRepository extends R2dbcRepository<Transaction, Long> {

  /**
   * Busca una transacción del usuario por su identificador.
   *
   * @param id     identificador de la transacción
   * @param userId identificador del usuario propietario
   * @return transacción encontrada envuelta en Mono
   */
  Mono<Transaction> findByIdAndUserId(Long id, Long userId);

  /**
   * Mueve a otra categoría todas las transacciones del usuario que lleva una dada.
   * Es lo que hace posible eliminar una categoría sin perder movimientos: como la
   * categoría es obligatoria, primero se reasignan y después se borra la fila.
   *
   * @param userId   identificador del usuario propietario
   * @param sourceId categoría que se está eliminando
   * @param targetId categoría que recibe las transacciones
   * @return número de transacciones reasignadas
   */
  @Modifying
  @Query("""
      UPDATE transactions
      SET category_id = :targetId
      WHERE user_id = :userId AND category_id = :sourceId
      """)
  Mono<Long> reassignCategory(Long userId, Long sourceId, Long targetId);
}
