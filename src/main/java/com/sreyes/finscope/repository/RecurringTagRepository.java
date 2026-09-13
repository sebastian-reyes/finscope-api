package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.RecurringTag;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link RecurringTag}.
 * Proporciona operaciones reactivas sobre la tabla `recurring_tags`, que enlaza cada
 * movimiento fijo con los tags que llevará el movimiento al confirmar cada mes.
 * Solo expone los borrados; las altas se hacen con las operaciones heredadas, porque
 * asignar tags a una plantilla es siempre un reemplazo completo de la lista, igual que en
 * las transacciones.
 */
@Repository
public interface RecurringTagRepository extends R2dbcRepository<RecurringTag, Long> {

  /**
   * Elimina todos los enlaces de una plantilla, dejándola sin tags.
   * Los tags en sí no se tocan: siguen en el catálogo del usuario.
   *
   * @param recurringId identificador de la plantilla
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteByRecurringId(Long recurringId);

  /**
   * Elimina todos los enlaces de un tag, retirándolo de las plantillas que lo llevan.
   * Es el paso previo a borrar el tag del catálogo: las plantillas sobreviven, solo dejan
   * de estar clasificadas por él.
   *
   * @param tagId identificador del tag
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteByTagId(Long tagId);
}
