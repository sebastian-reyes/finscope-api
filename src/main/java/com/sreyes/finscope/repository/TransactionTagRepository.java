package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.TransactionTag;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link TransactionTag}.
 * Proporciona operaciones reactivas sobre la tabla `transaction_tags`, que enlaza cada
 * transacción con los tags que lleva.
 * Expone el borrado de enlaces y la copia de los de un movimiento fijo; las altas normales
 * se hacen con las operaciones heredadas, porque asignar tags es siempre un reemplazo
 * completo de la lista.
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

  /**
   * Elimina todos los enlaces de un tag, retirándolo de las transacciones que lo llevan.
   * Es el paso previo a borrar el tag: las transacciones sobreviven, solo dejan de estar
   * clasificadas por él.
   *
   * @param tagId identificador del tag
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteByTagId(Long tagId);

  /**
   * Copia al movimiento los tags de la plantilla que lo origina.
   * Es lo que hace que confirmar un mes registre el movimiento clasificado igual que si se
   * hubiera escrito a mano, sin obligar al cliente a repetir los tags cada mes.
   *
   * <p>Se copian los identificadores, no los nombres: el catálogo es el mismo para los dos
   * lados, así que no hay nada que resolver ni que dar de alta, y renombrar el tag después
   * arrastra al fijo y al movimiento por igual. Un solo INSERT ... SELECT en lugar de leer
   * los tags y volver a escribirlos uno a uno.</p>
   *
   * <p>El conflicto se ignora para que la operación sea repetible: si el enlace ya estuviera,
   * volver a copiarlo no es un error.</p>
   *
   * @param transactionId identificador del movimiento que acaba de registrarse
   * @param recurringId   identificador de la plantilla que lo origina
   * @return número de enlaces creados, cero si la plantilla no lleva tags
   */
  @Modifying
  @Query("""
      INSERT INTO transaction_tags (transaction_id, tag_id)
      SELECT :transactionId, rt.tag_id
      FROM recurring_tags rt
      WHERE rt.recurring_id = :recurringId
      ON CONFLICT DO NOTHING
      """)
  Mono<Long> copyFromRecurring(Long transactionId, Long recurringId);
}
