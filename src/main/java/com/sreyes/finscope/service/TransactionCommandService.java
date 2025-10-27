package com.sreyes.finscope.service;

import com.sreyes.finscope.model.entity.Transaction;
import reactor.core.publisher.Mono;

/**
 * Servicio para la gestión de comandos de transacciones.
 * Define operaciones reactivas para crear, actualizar y eliminar transacciones.
 */
public interface TransactionCommandService {

  /**
   * Crea una nueva transacción.
   *
   * @param transaction entidad de transacción a crear
   * @return transacción creada envuelta en Mono
   */
  Mono<Transaction> createTransaction(Transaction transaction);

  /**
   * Elimina una transacción por su identificador.
   *
   * @param id identificador de la transacción
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteTransactionById(Long id);

  /**
   * Actualiza una transacción existente.
   *
   * @param id identificador de la transacción
   * @param transaction entidad de transacción con los datos actualizados
   * @return transacción actualizada envuelta en Mono
   */
  Mono<Transaction> updateTransaction(Long id, Transaction transaction);
}
