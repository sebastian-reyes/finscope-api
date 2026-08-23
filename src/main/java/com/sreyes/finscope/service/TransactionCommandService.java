package com.sreyes.finscope.service;

import com.sreyes.finscope.api.model.CreateTransactionRequest;
import com.sreyes.finscope.api.model.UpdateTransactionRequest;
import com.sreyes.finscope.model.entity.Transaction;
import reactor.core.publisher.Mono;

/**
 * Servicio para la gestión de comandos de transacciones.
 * Define operaciones reactivas para crear, actualizar y eliminar transacciones, incluidos
 * sus tags, que se crean y se borran junto con ella.
 * Todas las operaciones están acotadas al usuario propietario de los datos.
 */
public interface TransactionCommandService {

  /**
   * Crea una nueva transacción junto con sus tags.
   *
   * @param userId  identificador del usuario propietario
   * @param request datos de la transacción a crear
   * @return transacción creada envuelta en Mono
   */
  Mono<Transaction> createTransaction(Long userId, CreateTransactionRequest request);

  /**
   * Actualiza una transacción existente. Solo se modifican los campos informados; si la
   * petición incluye tags, estos reemplazan por completo a los actuales.
   *
   * @param userId  identificador del usuario propietario
   * @param id      identificador de la transacción
   * @param request datos a actualizar
   * @return transacción actualizada envuelta en Mono
   */
  Mono<Transaction> updateTransaction(Long userId, Long id, UpdateTransactionRequest request);

  /**
   * Elimina una transacción por su identificador junto con sus tags.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la transacción
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteTransactionById(Long userId, Long id);
}
