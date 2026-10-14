package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.LoansApi;
import com.sreyes.finscope.api.model.LoanResponse;
import com.sreyes.finscope.api.model.SaveLoanPaymentRequest;
import com.sreyes.finscope.api.model.SaveLoanRequest;
import com.sreyes.finscope.api.model.UpdateLoanRequest;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.LoanService;
import com.sreyes.finscope.util.mapper.LoanMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST del dinero que el usuario prestó a otras personas.
 * Implementa el contrato {@link LoansApi} generado a partir de la especificación OpenAPI.
 *
 * El alcance de cada operación lo resuelve {@link LoanService}; aquí solo se propaga el
 * usuario autenticado y se da forma a la respuesta.
 */
@RestController
@RequiredArgsConstructor
public class LoanController implements LoansApi {

  private final LoanService loanService;
  private final LoanMapper loanMapper;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<Flux<LoanResponse>>> listLoans(ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMapMany(loanService::findLoans)
        .map(loanMapper::toResponse)
        .collectList()
        .map(loans -> ResponseEntity.ok(Flux.fromIterable(loans)));
  }

  @Override
  public Mono<ResponseEntity<LoanResponse>> getLoan(Long id, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> loanService.findLoan(userId, id))
        .map(loanMapper::toResponse)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<LoanResponse>> createLoan(Mono<SaveLoanRequest> saveLoanRequest,
                                                       ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(saveLoanRequest)
        .flatMap(tuple -> loanService.createLoan(tuple.getT1(), tuple.getT2()))
        .map(loanMapper::toResponse)
        .map(loan -> ResponseEntity.status(HttpStatus.CREATED).body(loan));
  }

  @Override
  public Mono<ResponseEntity<LoanResponse>> updateLoan(
      Long id, Mono<UpdateLoanRequest> updateLoanRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(updateLoanRequest)
        .flatMap(tuple -> loanService.updateLoan(tuple.getT1(), id, tuple.getT2()))
        .map(loanMapper::toResponse)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> deleteLoan(Long id, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> loanService.deleteLoan(userId, id))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<LoanResponse>> createLoanPayment(
      Long id, Mono<SaveLoanPaymentRequest> saveLoanPaymentRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(saveLoanPaymentRequest)
        .flatMap(tuple -> loanService.addPayment(tuple.getT1(), id, tuple.getT2()))
        .map(loanMapper::toResponse)
        .map(loan -> ResponseEntity.status(HttpStatus.CREATED).body(loan));
  }

  @Override
  public Mono<ResponseEntity<LoanResponse>> deleteLoanPayment(Long id, Long paymentId,
                                                              ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> loanService.deletePayment(userId, id, paymentId))
        .map(loanMapper::toResponse)
        .map(ResponseEntity::ok);
  }
}
