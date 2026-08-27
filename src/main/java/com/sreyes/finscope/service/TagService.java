package com.sreyes.finscope.service;

import com.sreyes.finscope.model.query.TagUsage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio de gestión del catálogo de tags del usuario.
 * Los tags siguen siendo texto libre que nace al escribirlo dentro de una transacción: el
 * alta explícita es una comodidad, no la vía habitual. Lo que sí hacía falta es poder
 * corregir y limpiar el catálogo, porque un tag mal escrito se queda ocupando su nombre y
 * ensuciando el autocompletado hasta que alguien lo retire.
 */
public interface TagService {

  /**
   * Obtiene el catálogo completo de tags del usuario con el uso que les está dando.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con los tags, en orden alfabético
   */
  Flux<TagUsage> findTags(Long userId);

  /**
   * Da de alta un tag en el catálogo del usuario.
   *
   * @param userId identificador del usuario propietario
   * @param name   nombre del tag
   * @return el tag creado, con su uso todavía a cero
   */
  Mono<TagUsage> createTag(Long userId, String name);

  /**
   * Renombra un tag del usuario, lo que lo cambia en todas las transacciones que lo llevan.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del tag
   * @param name   nombre nuevo
   * @return el tag renombrado
   */
  Mono<TagUsage> renameTag(Long userId, Long id, String name);

  /**
   * Elimina un tag del catálogo y lo retira de las transacciones que lo llevan.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del tag
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteTag(Long userId, Long id);
}
