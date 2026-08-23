package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.TransactionsApi;
import com.sreyes.finscope.api.model.CreateTransactionRequest;
import com.sreyes.finscope.api.model.TransactionPageResponse;
import com.sreyes.finscope.api.model.TransactionResponse;
import com.sreyes.finscope.api.model.UpdateTransactionRequest;
import com.sreyes.finscope.model.query.TransactionSearchCriteria;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TransactionCommandService;
import com.sreyes.finscope.service.TransactionQueryService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para gestionar operaciones relacionadas con transacciones.
 * Implementa el contrato {@link TransactionsApi} generado a partir de la especificación
 * OpenAPI y mantiene separadas la escritura, delegada en {@link TransactionCommandService},
 * y la lectura, delegada en {@link TransactionQueryService}. Las transacciones pertenecen a
 * un usuario, por lo que toda operación resuelve primero el usuario autenticado y se lo
 * propaga a los servicios.
 */
@RestController
@RequiredArgsConstructor
public class TransactionController implements TransactionsApi {

  private final TransactionCommandService transactionCommandService;
  private final TransactionQueryService transactionQueryService;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<TransactionPageResponse>> listTransactions(
      Integer month, Integer year, LocalDateTime dateFrom, LocalDateTime dateTo,
      Long transactionTypeId, String tag, Integer page, Integer size, String sort,
      ServerWebExchange exchange) {
    TransactionSearchCriteria criteria = new TransactionSearchCriteria(month, year, dateFrom,
        dateTo, transactionTypeId, tag, page, size, sort);
    return authenticatedUser.currentUserId()
        .flatMap(userId -> transactionQueryService.searchTransactions(userId, criteria))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<TransactionResponse>> getTransactionById(Long id,
                                                                      ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> transactionQueryService.getTransactionById(userId, id))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<TransactionResponse>> createTransaction(
      Mono<CreateTransactionRequest> createTransactionRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(createTransactionRequest)
        .flatMap(tuple -> transactionCommandService.createTransaction(tuple.getT1(),
                tuple.getT2())
            .flatMap(transaction -> transactionQueryService.getTransactionById(tuple.getT1(),
                transaction.getId())))
        .map(transaction -> ResponseEntity.status(HttpStatus.CREATED).body(transaction));
  }

  @Override
  public Mono<ResponseEntity<TransactionResponse>> updateTransaction(
      Long id, Mono<UpdateTransactionRequest> updateTransactionRequest,
      ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(updateTransactionRequest)
        .flatMap(tuple -> transactionCommandService.updateTransaction(tuple.getT1(), id,
                tuple.getT2())
            .flatMap(transaction -> transactionQueryService.getTransactionById(tuple.getT1(),
                transaction.getId())))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> deleteTransaction(Long id, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> transactionCommandService.deleteTransactionById(userId, id))
        .thenReturn(ResponseEntity.noContent().build());
  }
}
