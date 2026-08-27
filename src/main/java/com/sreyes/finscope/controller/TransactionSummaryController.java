package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.SummaryApi;
import com.sreyes.finscope.api.model.SummaryGranularity;
import com.sreyes.finscope.api.model.SummarySeriesResponse;
import com.sreyes.finscope.api.model.TransactionSummaryResponse;
import com.sreyes.finscope.model.query.TransactionSummaryCriteria;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TransactionSummaryService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para consultar los agregados financieros del usuario.
 * Implementa el contrato {@link SummaryApi} generado a partir de la especificación OpenAPI.
 * Vive separado de {@link TransactionController} porque responde a otra pregunta: aquel
 * devuelve transacciones y este devuelve cuánto suman. Los filtros son los mismos que los
 * del listado, de modo que un resumen siempre corresponde con lo que devolvería la consulta
 * con esos mismos filtros.
 */
@RestController
@RequiredArgsConstructor
public class TransactionSummaryController implements SummaryApi {

  private final TransactionSummaryService transactionSummaryService;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<TransactionSummaryResponse>> getTransactionSummary(
      Integer month, Integer year, LocalDateTime dateFrom, LocalDateTime dateTo,
      Long transactionTypeId, Long categoryId, String tag, ServerWebExchange exchange) {
    TransactionSummaryCriteria criteria = new TransactionSummaryCriteria(month, year, dateFrom,
        dateTo, transactionTypeId, categoryId, tag);
    return authenticatedUser.currentUserId()
        .flatMap(userId -> transactionSummaryService.summarize(userId, criteria))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<SummarySeriesResponse>> getTransactionSummarySeries(
      Integer month, Integer year, LocalDateTime dateFrom, LocalDateTime dateTo,
      Long transactionTypeId, Long categoryId, String tag, SummaryGranularity granularity,
      ServerWebExchange exchange) {
    TransactionSummaryCriteria criteria = new TransactionSummaryCriteria(month, year, dateFrom,
        dateTo, transactionTypeId, categoryId, tag);
    return authenticatedUser.currentUserId()
        .flatMap(userId -> transactionSummaryService.summarizeSeries(userId, criteria,
            granularity))
        .map(ResponseEntity::ok);
  }
}
