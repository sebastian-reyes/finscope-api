package com.sreyes.finscope.controller;

import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.service.TransactionTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/transaction-types")
@RequiredArgsConstructor
public class TransactionTypeController {

  private final TransactionTypeService transactionTypeService;

  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public Flux<TransactionType> getAllTransactionTypes() {
    return transactionTypeService.findAllTransactionTypes();
  }

  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Mono<TransactionType> getTransactionTypeById(@PathVariable Long id) {
    return transactionTypeService.findTransactionTypeById(id);
  }

}
