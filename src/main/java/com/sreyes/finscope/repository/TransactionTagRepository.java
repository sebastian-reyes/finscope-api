package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.TransactionTag;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link TransactionTag}.
 * Proporciona operaciones reactivas sobre la tabla `transaction_tags`, que enlaza cada
 * transacción con los tags que lleva.
 * Solo expone el borrado de los enlaces de una transacción; las altas se hacen con las
 * operaciones heredadas, porque asignar tags es siempre un reemplazo completo de la lista.
 */
@Repository
public interface TransactionTagRepository extends R2dbcRepository<TransactionTag, Long> {

  /**
   * Elimina todos los enlaces de una transacción, dejándola sin tags.
   * Los tags en sí no se tocan: siguen en el catálogo del usuario.
   *
   * @param transactionId identificador de la transacción
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteByTransactionId(Long transactionId);
}
