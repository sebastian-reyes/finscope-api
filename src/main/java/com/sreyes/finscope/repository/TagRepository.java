package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Tag;
import java.util.Collection;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link Tag}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `tags`.
 * Un tag no existe fuera de su transacción, por lo que las consultas parten siempre de
 * ella; las que se acotan al usuario lo hacen a través de la transacción propietaria.
 */
@Repository
public interface TagRepository extends R2dbcRepository<Tag, Long> {

  /**
   * Obtiene en una sola consulta los tags de todas las transacciones indicadas.
   *
   * @param transactionIds identificadores de las transacciones
   * @return flujo reactivo de tags
   */
  Flux<Tag> findByTransactionIdIn(Collection<Long> transactionIds);

  /**
   * Elimina todos los tags de una transacción.
   *
   * @param transactionId identificador de la transacción
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteByTransactionId(Long transactionId);

  /**
   * Obtiene los nombres distintos de tag que el usuario ya ha usado, en orden alfabético.
   * Alimenta el autocompletado del cliente, que de otro modo no tendría de dónde sacar
   * los tags existentes al no haber catálogo.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con los nombres de tag
   */
  @Query("""
      SELECT DISTINCT t.name_tag
      FROM tags t
      INNER JOIN transactions tr ON tr.id_transaction = t.transaction_id
      WHERE tr.user_id = :userId
      ORDER BY t.name_tag
      """)
  Flux<String> findDistinctNamesByUserId(Long userId);

  /**
   * Obtiene los identificadores de las transacciones del usuario que llevan el tag
   * indicado, sin distinguir mayúsculas de minúsculas.
   *
   * @param userId identificador del usuario propietario
   * @param name   nombre del tag
   * @return flujo reactivo con los identificadores de transacción
   */
  @Query("""
      SELECT t.transaction_id
      FROM tags t
      INNER JOIN transactions tr ON tr.id_transaction = t.transaction_id
      WHERE tr.user_id = :userId AND LOWER(t.name_tag) = LOWER(:name)
      """)
  Flux<Long> findTransactionIdsByUserIdAndName(Long userId, String name);
}
