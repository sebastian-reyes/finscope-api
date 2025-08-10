package com.sreyes.finscope.controller;

import com.sreyes.finscope.model.dto.TransactionResponseDTO;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.service.TransactionCommandService;
import com.sreyes.finscope.service.TransactionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/transactions")
public class TransactionController {

  private final TransactionCommandService transactionCommandService;
  private final TransactionQueryService transactionQueryService;

  @PostMapping
  public Mono<Transaction> createTransaction(@RequestBody Transaction transaction) {
    return transactionCommandService.createTransaction(transaction);
  }

  @PatchMapping("/{id}")
  public Mono<Transaction> updateTransaction(@PathVariable Long id, @RequestBody Transaction transaction) {
    return transactionCommandService.updateTransaction(id, transaction);
  }

  @DeleteMapping("/{id}")
  public Mono<Void> deleteTransactionById(@PathVariable Long id) {
    return transactionCommandService.deleteTransactionById(id);
  }

  @GetMapping
  public Flux<TransactionResponseDTO> getAllTransactions() {
    return transactionQueryService.getAllTransactions();
  }

  @GetMapping("/{id}")
  public Mono<TransactionResponseDTO> getTransactionById(@PathVariable Long id) {
    return transactionQueryService.getTransactionById(id);
  }

  @GetMapping("/transaction-type/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Flux<TransactionResponseDTO> getTransactionsByTypeId(@PathVariable Long id) {
    return transactionQueryService.getAllTransactionsByTypeId(id);
  }

  @GetMapping("/category/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Flux<TransactionResponseDTO> getTransactionsByCategoryId(@PathVariable Long id) {
    return transactionQueryService.getAllTransactionsByCategoryId(id);
  }

  @GetMapping("/filter")
  public Flux<TransactionResponseDTO> getTransactionsByMonthAndYear(@RequestParam Integer month, @RequestParam Integer year) {
    return transactionQueryService.getTransactionsByMonthAndYear(month, year);
  }
}
