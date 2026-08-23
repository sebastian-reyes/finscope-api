package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.TransactionTypesApi;
import com.sreyes.finscope.api.model.TransactionTypeResponse;
import com.sreyes.finscope.service.TransactionTypeService;
import com.sreyes.finscope.util.mapper.TransactionTypeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para gestionar los tipos de transacción.
 * Implementa el contrato {@link TransactionTypesApi} generado a partir de la especificación
 * OpenAPI y expone la consulta de todos los tipos y la de uno por su identificador.
 */
@RestController
@RequiredArgsConstructor
public class TransactionTypeController implements TransactionTypesApi {

  private final TransactionTypeService transactionTypeService;
  private final TransactionTypeMapper transactionTypeMapper;

  @Override
  public Mono<ResponseEntity<Flux<TransactionTypeResponse>>> listTransactionTypes(
      ServerWebExchange exchange) {
    return transactionTypeService.findAllTransactionTypes()
        .map(transactionTypeMapper::toResponse)
        .collectList()
        .map(types -> ResponseEntity.ok(Flux.fromIterable(types)));
  }

  @Override
  public Mono<ResponseEntity<TransactionTypeResponse>> getTransactionTypeById(
      Long id, ServerWebExchange exchange) {
    return transactionTypeService.findTransactionTypeById(id)
        .map(transactionTypeMapper::toResponse)
        .map(ResponseEntity::ok);
  }
}
