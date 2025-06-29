package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import com.sreyes.finscope.service.TransactionTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class TransactionTypeServiceImpl implements TransactionTypeService {

  private final TransactionTypeRepository transactionTypeRepository;

  @Override
  public Flux<TransactionType> findAllTransactionTypes() {
    return transactionTypeRepository.findAll();
  }

  @Override
  public Mono<TransactionType> findTransactionTypeById(Long id) {
    return transactionTypeRepository.findById(id)
        .switchIfEmpty(Mono
            .error(new TransactionTypeNotFoundException(
                "Transaction Type not found with id: " + id)));
  }
}
