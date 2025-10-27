package com.sreyes.finscope.service;

import com.sreyes.finscope.model.entity.TransactionType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio para operaciones relacionadas con los tipos de transacción.
 * Proporciona métodos para consultar todos los tipos y buscar por ID.
 */
public interface TransactionTypeService {
  /**
   * Obtiene todos los tipos de transacción disponibles.
   *
   * @return un {@link Flux} que emite los tipos de transacción encontrados
   */
  Flux<TransactionType> findAllTransactionTypes();

  /**
   * Busca un tipo de transacción por su identificador único.
   *
   * @param id el identificador del tipo de transacción
   * @return un {@link Mono} que emite el tipo de transacción encontrado, o vacío si no existe
   */
  Mono<TransactionType> findTransactionTypeById(Long id);
}
