package com.sreyes.finscope.service;

import com.sreyes.finscope.model.entity.TransactionType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransactionTypeService {
  Flux<TransactionType> findAllTransactionTypes();
  Mono<TransactionType> findTransactionTypeById(Long id);
}
