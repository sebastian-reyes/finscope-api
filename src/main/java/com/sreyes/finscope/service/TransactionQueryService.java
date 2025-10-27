package com.sreyes.finscope.service;

import com.sreyes.finscope.model.dto.TransactionResponseDto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio para la consulta de transacciones.
 * Define operaciones reactivas para obtener transacciones filtradas por tipo, categoría, fecha o identificador.
 */
public interface TransactionQueryService {

  /**
   * Obtiene todas las transacciones.
   *
   * @return flujo reactivo de transacciones
   */
  Flux<TransactionResponseDto> getAllTransactions();

  /**
   * Obtiene todas las transacciones filtradas por tipo.
   *
   * @param id identificador del tipo de transacción
   * @return flujo reactivo de transacciones filtradas por tipo
   */
  Flux<TransactionResponseDto> getAllTransactionsByTypeId(Long id);

  /**
   * Obtiene todas las transacciones filtradas por categoría.
   *
   * @param id identificador de la categoría
   * @return flujo reactivo de transacciones filtradas por categoría
   */
  Flux<TransactionResponseDto> getAllTransactionsByCategoryId(Long id);

  /**
   * Busca una transacción por su identificador.
   *
   * @param id identificador de la transacción
   * @return transacción encontrada envuelta en Mono
   */
  Mono<TransactionResponseDto> getTransactionById(Long id);

  /**
   * Obtiene transacciones filtradas por mes y año.
   *
   * @param month mes de la transacción
   * @param year año de la transacción
   * @return flujo reactivo de transacciones filtradas por mes y año
   */
  Flux<TransactionResponseDto> getTransactionsByMonthAndYear(Integer month, Integer year);
}