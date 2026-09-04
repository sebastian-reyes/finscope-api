package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.RecurringApi;
import com.sreyes.finscope.api.model.ConfirmRecurringTransactionRequest;
import com.sreyes.finscope.api.model.RecurringOccurrenceResponse;
import com.sreyes.finscope.api.model.RecurringTransactionResponse;
import com.sreyes.finscope.api.model.SaveRecurringTransactionRequest;
import com.sreyes.finscope.api.model.SkipRecurringTransactionRequest;
import com.sreyes.finscope.api.model.UpdateRecurringTransactionRequest;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.RecurringTransactionService;
import com.sreyes.finscope.util.mapper.RecurringTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para gestionar los movimientos fijos del usuario.
 * Implementa el contrato {@link RecurringApi} generado a partir de la especificación
 * OpenAPI.
 *
 * Vive separado de {@link TransactionController} aunque acabe creando transacciones, porque
 * responde a otra pregunta: aquel registra lo que pasó y este dice lo que se repite y qué
 * falta por pagar este mes. El alcance de cada operación lo resuelve
 * {@link RecurringTransactionService} y aquí solo se propaga el usuario autenticado.
 */
@RestController
@RequiredArgsConstructor
public class RecurringController implements RecurringApi {

  private final RecurringTransactionService recurringTransactionService;
  private final RecurringTransactionMapper recurringTransactionMapper;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<Flux<RecurringOccurrenceResponse>>> listRecurringTransactions(
      Integer month, Integer year, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMapMany(userId -> recurringTransactionService.findRecurring(userId, month, year))
        .map(recurringTransactionMapper::toResponse)
        .collectList()
        .map(recurring -> ResponseEntity.ok(Flux.fromIterable(recurring)));
  }

  @Override
  public Mono<ResponseEntity<RecurringTransactionResponse>> createRecurringTransaction(
      Mono<SaveRecurringTransactionRequest> saveRecurringTransactionRequest,
      ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(saveRecurringTransactionRequest)
        .flatMap(tuple -> recurringTransactionService.createRecurring(tuple.getT1(),
            tuple.getT2()))
        .map(recurringTransactionMapper::toResponse)
        .map(recurring -> ResponseEntity.status(HttpStatus.CREATED).body(recurring));
  }

  @Override
  public Mono<ResponseEntity<RecurringTransactionResponse>> updateRecurringTransaction(
      Long id, Mono<UpdateRecurringTransactionRequest> updateRecurringTransactionRequest,
      ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(updateRecurringTransactionRequest)
        .flatMap(tuple -> recurringTransactionService.updateRecurring(tuple.getT1(), id,
            tuple.getT2()))
        .map(recurringTransactionMapper::toResponse)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> deleteRecurringTransaction(Long id,
                                                               ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> recurringTransactionService.deleteRecurring(userId, id))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<RecurringOccurrenceResponse>> confirmRecurringTransaction(
      Long id, Mono<ConfirmRecurringTransactionRequest> confirmRecurringTransactionRequest,
      ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(confirmRecurringTransactionRequest)
        .flatMap(tuple -> recurringTransactionService.confirmRecurring(tuple.getT1(), id,
            tuple.getT2()))
        .map(recurringTransactionMapper::toResponse)
        .map(occurrence -> ResponseEntity.status(HttpStatus.CREATED).body(occurrence));
  }

  @Override
  public Mono<ResponseEntity<RecurringOccurrenceResponse>> skipRecurringTransaction(
      Long id, Mono<SkipRecurringTransactionRequest> skipRecurringTransactionRequest,
      ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(skipRecurringTransactionRequest)
        .flatMap(tuple -> recurringTransactionService.skipRecurring(tuple.getT1(), id,
            tuple.getT2().getMonth(), tuple.getT2().getYear()))
        .map(recurringTransactionMapper::toResponse)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<RecurringOccurrenceResponse>> unskipRecurringTransaction(
      Long id, Integer month, Integer year, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> recurringTransactionService.unskipRecurring(userId, id, month, year))
        .map(recurringTransactionMapper::toResponse)
        .map(ResponseEntity::ok);
  }
}
