package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.model.dto.TransactionResponseDTO;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import com.sreyes.finscope.service.TransactionService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.mapper.MapperUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final MapperUtil mapperUtil;

  @Override
  public Mono<Transaction> createTransaction(Transaction transaction) {
    transaction.setDate(LocalDateTime.now());
    return transactionRepository.save(transaction);
  }

  @Override
  public Flux<TransactionResponseDTO> getAllTransactions() {
    return transactionRepository.findAll()
        .flatMap(transaction -> categoryRepository.findById(transaction.getCategoryId())
            .flatMap(category -> transactionTypeRepository
                .findById(transaction.getTransactionTypeId())
                .map(transactionType -> mapperUtil
                    .buildTransactionResponseDTO(transaction, category, transactionType))
            )
        );
  }

  @Override
  public Flux<TransactionResponseDTO> getTransactionsByTypeId(Long id) {
    return transactionRepository.findTransactionsByTypeId(id)
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)));
  }

  @Override
  public Mono<TransactionResponseDTO> getTransactionById(Long id) {
    return transactionRepository.findById(id)
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)))
        .flatMap(transaction -> categoryRepository.findById(transaction.getCategoryId())
            .flatMap(category -> transactionTypeRepository
                .findById(transaction.getTransactionTypeId())
                .map(transactionType -> mapperUtil
                    .buildTransactionResponseDTO(transaction, category, transactionType))
            )
        );
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
