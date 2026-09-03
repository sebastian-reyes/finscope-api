package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.BudgetsApi;
import com.sreyes.finscope.api.model.BudgetResponse;
import com.sreyes.finscope.api.model.CopyBudgetsRequest;
import com.sreyes.finscope.api.model.SaveBudgetRequest;
import com.sreyes.finscope.api.model.UpdateBudgetRequest;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.BudgetService;
import com.sreyes.finscope.util.mapper.BudgetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para gestionar los presupuestos mensuales del usuario.
 * Implementa el contrato {@link BudgetsApi} generado a partir de la especificación OpenAPI.
 *
 * Vive separado de {@link CategoryController} aunque el presupuesto sea de una categoría,
 * porque responde a otra pregunta: aquel devuelve el catálogo y este devuelve cuánto se
 * pensaba gastar en él y cuánto se lleva gastado. El alcance de cada operación lo resuelve
 * {@link BudgetService} y aquí solo se propaga el usuario autenticado.
 */
@RestController
@RequiredArgsConstructor
public class BudgetController implements BudgetsApi {

  private final BudgetService budgetService;
  private final BudgetMapper budgetMapper;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<Flux<BudgetResponse>>> listBudgets(
      Integer month, Integer year, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMapMany(userId -> budgetService.findBudgets(userId, month, year))
        .map(budgetMapper::toResponse)
        .collectList()
        .map(budgets -> ResponseEntity.ok(Flux.fromIterable(budgets)));
  }

  @Override
  public Mono<ResponseEntity<BudgetResponse>> createBudget(
      Mono<SaveBudgetRequest> saveBudgetRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(saveBudgetRequest)
        .flatMap(tuple -> budgetService.createBudget(tuple.getT1(), tuple.getT2().getCategoryId(),
            tuple.getT2().getMonth(), tuple.getT2().getYear(), tuple.getT2().getAmount()))
        .map(budgetMapper::toResponse)
        .map(budget -> ResponseEntity.status(HttpStatus.CREATED).body(budget));
  }

  @Override
  public Mono<ResponseEntity<BudgetResponse>> updateBudget(
      Long id, Mono<UpdateBudgetRequest> updateBudgetRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(updateBudgetRequest)
        .flatMap(tuple -> budgetService.updateBudget(tuple.getT1(), id,
            tuple.getT2().getAmount()))
        .map(budgetMapper::toResponse)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> deleteBudget(Long id, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> budgetService.deleteBudget(userId, id))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<Flux<BudgetResponse>>> copyBudgets(
      Mono<CopyBudgetsRequest> copyBudgetsRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(copyBudgetsRequest)
        .flatMapMany(tuple -> budgetService.copyBudgets(tuple.getT1(),
            tuple.getT2().getSourceMonth(), tuple.getT2().getSourceYear(),
            tuple.getT2().getMonth(), tuple.getT2().getYear()))
        .map(budgetMapper::toResponse)
        .collectList()
        .map(budgets -> ResponseEntity.ok(Flux.fromIterable(budgets)));
  }
}
