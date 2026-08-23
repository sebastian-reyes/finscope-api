package com.sreyes.finscope.service;

import reactor.core.publisher.Flux;

/**
 * Servicio de consulta de los tags en uso.
 * Los tags son texto libre y se crean dentro de la transacción, por lo que no hay un
 * catálogo que mantener ni operaciones de escritura propias. Lo único que necesita el
 * cliente es saber qué tags ha usado ya el usuario para poder sugerirlos.
 */
public interface TagService {

  /**
   * Obtiene los nombres distintos de tag que el usuario ya ha usado, en orden alfabético.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con los nombres de tag
   */
  Flux<String> findUsedTagNames(Long userId);
}
