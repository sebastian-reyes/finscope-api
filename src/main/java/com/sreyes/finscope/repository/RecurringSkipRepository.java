package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.RecurringSkip;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link RecurringSkip}.
 * Guarda los meses en los que un movimiento fijo no toca.
 *
 * Las dos operaciones son idempotentes a propósito: omitir dos veces deja el mismo mes
 * omitido y deshacer una omisión que no existía no es un error. Es lo que permite que los
 * dos botones de la fila se puedan pulsar sin comprobar antes en qué estado estaban.
 */
@Repository
public interface RecurringSkipRepository extends R2dbcRepository<RecurringSkip, Long> {

  /**
   * Omite un mes si no lo estaba ya.
   * Se apoya en la restricción de unicidad de la base en lugar de comprobar antes si está
   * libre, porque entre la comprobación y la inserción podría colarse otra petición del
   * mismo usuario omitiendo ese mismo mes.
   *
   * @param recurringId identificador de la plantilla
   * @param month       mes que se omite
   * @param year        año del mes que se omite
   * @return número de filas insertadas, cero si el mes ya estaba omitido
   */
  @Modifying
  @Query("""
      INSERT INTO recurring_skips (recurring_id, month, year)
      VALUES (:recurringId, :month, :year)
      ON CONFLICT DO NOTHING
      """)
  Mono<Long> insertIfAbsent(Long recurringId, Integer month, Integer year);

  /**
   * Deshace la omisión de un mes. Si no estaba omitido no cambia nada.
   *
   * @param recurringId identificador de la plantilla
   * @param month       mes cuya omisión se deshace
   * @param year        año de ese mes
   * @return número de omisiones eliminadas, cero si no había ninguna
   */
  @Modifying
  @Query("""
      DELETE FROM recurring_skips
      WHERE recurring_id = :recurringId AND month = :month AND year = :year
      """)
  Mono<Long> deleteByPeriod(Long recurringId, Integer month, Integer year);
}
