package com.sreyes.finscope.service;

import com.sreyes.finscope.model.dto.TransactionResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionQueryService {
  Flux<TransactionResponseDTO> getAllTransactions();
  Flux<TransactionResponseDTO> getAllTransactionsByTypeId(Long id);
  Flux<TransactionResponseDTO> getAllTransactionsByCategoryId(Long id);
  Mono<TransactionResponseDTO> getTransactionById(Long id);
  Flux<TransactionResponseDTO> getTransactionsByMonthAndYear(Integer month, Integer year);
}
