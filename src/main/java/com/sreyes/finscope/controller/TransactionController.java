package com.sreyes.finscope.controller;

import com.sreyes.finscope.model.dto.TransactionResponseDto;
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


/**
 * Controlador REST para gestionar operaciones relacionadas con transacciones.
 * Proporciona endpoints para crear, actualizar, eliminar y consultar transacciones.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/transactions")
public class TransactionController {

  private final TransactionCommandService transactionCommandService;
  private final TransactionQueryService transactionQueryService;

  /**
   * Crea una nueva transacción.
   *
   * @param transaction la transacción a crear.
   * @return un {@link Mono} con la transacción creada.
   */
  @PostMapping
  public Mono<Transaction> createTransaction(@RequestBody Transaction transaction) {
    return transactionCommandService.createTransaction(transaction);
  }

  /**
   * Actualiza una transacción existente.
   *
   * @param id          el identificador de la transacción.
   * @param transaction los datos actualizados de la transacción.
   * @return un {@link Mono} con la transacción actualizada.
   */
  @PatchMapping("/{id}")
  public Mono<Transaction> updateTransaction(@PathVariable Long id,
                                             @RequestBody Transaction transaction) {
    return transactionCommandService.updateTransaction(id, transaction);
  }

  /**
   * Elimina una transacción por su identificador.
   *
   * @param id el identificador de la transacción a eliminar.
   * @return un {@link Mono} vacío cuando la eliminación se completa.
   */
  @DeleteMapping("/{id}")
  public Mono<Void> deleteTransactionById(@PathVariable Long id) {
    return transactionCommandService.deleteTransactionById(id);
  }

  /**
   * Obtiene todas las transacciones.
   *
   * @return un {@link Flux} de {@link TransactionResponseDto} con todas las transacciones.
   */
  @GetMapping
  public Flux<TransactionResponseDto> getAllTransactions() {
    return transactionQueryService.getAllTransactions();
  }

  /**
   * Obtiene una transacción por su identificador.
   *
   * @param id el identificador de la transacción.
   * @return un {@link Mono} de {@link TransactionResponseDto} si existe, vacío en caso contrario.
   */
  @GetMapping("/{id}")
  public Mono<TransactionResponseDto> getTransactionById(@PathVariable Long id) {
    return transactionQueryService.getTransactionById(id);
  }

  /**
   * Obtiene todas las transacciones por el identificador de tipo de transacción.
   *
   * @param id el identificador del tipo de transacción.
   * @return un {@link Flux} de {@link TransactionResponseDto} filtradas por tipo.
   */
  @GetMapping("/transaction-type/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Flux<TransactionResponseDto> getTransactionsByTypeId(@PathVariable Long id) {
    return transactionQueryService.getAllTransactionsByTypeId(id);
  }

  /**
   * Obtiene todas las transacciones por el identificador de categoría.
   *
   * @param id el identificador de la categoría.
   * @return un {@link Flux} de {@link TransactionResponseDto} filtradas por categoría.
   */
  @GetMapping("/category/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Flux<TransactionResponseDto> getTransactionsByCategoryId(@PathVariable Long id) {
    return transactionQueryService.getAllTransactionsByCategoryId(id);
  }

  /**
   * Obtiene todas las transacciones filtradas por mes y año.
   *
   * @param month el mes de la transacción.
   * @param year  el año de la transacción.
   * @return un {@link Flux} de {@link TransactionResponseDto} filtradas por mes y año.
   */
  @GetMapping("/filter")
  public Flux<TransactionResponseDto> getTransactionsByMonthAndYear(@RequestParam Integer month,
                                                                    @RequestParam Integer year) {
    return transactionQueryService.getTransactionsByMonthAndYear(month, year);
  }
}