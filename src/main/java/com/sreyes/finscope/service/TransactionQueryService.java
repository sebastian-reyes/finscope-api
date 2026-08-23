package com.sreyes.finscope.service;

import com.sreyes.finscope.api.model.TransactionPageResponse;
import com.sreyes.finscope.api.model.TransactionResponse;
import com.sreyes.finscope.model.query.TransactionSearchCriteria;
import reactor.core.publisher.Mono;

/**
 * Servicio para la consulta de transacciones.
 * Define operaciones reactivas para obtener una transacción concreta y para buscar
 * transacciones aplicando filtros opcionales, paginación y ordenamiento.
 * Todas las operaciones están acotadas al usuario propietario de los datos.
 */
public interface TransactionQueryService {

  /**
   * Busca las transacciones que cumplen los criterios indicados.
   *
   * @param userId   identificador del usuario propietario
   * @param criteria filtros, paginación y ordenamiento solicitados
   * @return página de transacciones envuelta en Mono
   */
  Mono<TransactionPageResponse> searchTransactions(Long userId,
                                                   TransactionSearchCriteria criteria);

  /**
   * Busca una transacción por su identificador.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la transacción
   * @return transacción encontrada envuelta en Mono
   */
  Mono<TransactionResponse> getTransactionById(Long userId, Long id);
}
