package com.sreyes.finscope.service;

import com.sreyes.finscope.model.dto.TransactionResponseDTO;
import com.sreyes.finscope.model.entity.Transaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionService {

  Mono<Transaction> createTransaction(Transaction transaction);
  Flux<TransactionResponseDTO> getAllTransactions();
  Flux<TransactionResponseDTO> getAllTransactionsByTypeId(Long id);
  Flux<TransactionResponseDTO> getAllTransactionsByCategoryId(Long id);
  Mono<TransactionResponseDTO> getTransactionById(Long id);
  Mono<Void> deleteTransactionById(Long id);
  Mono<Transaction> updateTransaction(Long id, Transaction transaction);
  Flux<TransactionResponseDTO> getTransactionsByMonthAndYear(Integer month, Integer year);
}
