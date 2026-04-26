package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.service.TransactionCommandService;
import com.sreyes.finscope.util.constants.Constants;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link TransactionCommandService} para la gestión de comandos de transacciones.
 * Proporciona operaciones reactivas para crear, actualizar y eliminar transacciones.
 * Utiliza {@link TransactionRepository} para el acceso a datos.
 * Lanza {@link TransactionNotFoundException} cuando no se encuentra una transacción.
 */
@Service
@RequiredArgsConstructor
public class TransactionCommandServiceImpl implements TransactionCommandService {

  private final TransactionRepository transactionRepository;

  @Override
  public Mono<Transaction> createTransaction(Transaction transaction) {
    transaction.setDate(LocalDateTime.now());
    return transactionRepository.save(transaction);
  }

  @Override
  public Mono<Void> deleteTransactionById(Long id) {
    return transactionRepository.existsById(id)
        .filter(Boolean::booleanValue)
        .flatMap(found -> transactionRepository.deleteById(id).thenReturn(found))
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)))
        .then();
  }

  @Override
  public Mono<Transaction> updateTransaction(Long id, Transaction newTransaction) {
    return transactionRepository.findById(id)
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)))
        .map(existingTransaction -> {
          newTransaction.setId(id);
          newTransaction.setDate(
              Optional.ofNullable(existingTransaction.getDate())
                  .or(() -> Optional.ofNullable(newTransaction.getDate()))
                  .orElse(LocalDateTime.now())
          );
          return newTransaction;
        })
        .flatMap(transactionRepository::save);
  }

}
