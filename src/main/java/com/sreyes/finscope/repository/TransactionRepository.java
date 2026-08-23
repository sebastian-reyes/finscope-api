package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Transaction;
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
}
