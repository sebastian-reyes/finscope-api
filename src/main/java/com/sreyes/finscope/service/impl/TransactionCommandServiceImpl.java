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
        .flatMap(exists -> {
          if (Boolean.TRUE.equals(exists)) {
            return transactionRepository.deleteById(id);
          } else {
            return Mono.error(new TransactionNotFoundException(
                Constants.TRANSACTION_NOT_FOUND + id));
          }
        });
  }

  @Override
  public Mono<Transaction> updateTransaction(Long id, Transaction newTx) {
    return transactionRepository.findById(id)
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)))
        .map(existingTx -> {
          newTx.setId(id);
          newTx.setDate(
              Optional.ofNullable(existingTx.getDate())
                  .or(() -> Optional.ofNullable(newTx.getDate()))
                  .orElse(LocalDateTime.now())
          );
          return newTx;
        })
        .flatMap(transactionRepository::save);
  }

}
