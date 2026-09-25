package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.SummaryApi;
import com.sreyes.finscope.api.model.Currency;
import com.sreyes.finscope.api.model.SummaryGranularity;
import com.sreyes.finscope.api.model.SummarySeriesResponse;
import com.sreyes.finscope.api.model.TransactionSummaryResponse;
import com.sreyes.finscope.model.query.TransactionSummaryCriteria;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TransactionSummaryService;
import com.sreyes.finscope.util.rules.CurrencyRules;
import java.math.BigDecimal;
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
 * La moneda es el único filtro que el contrato da por supuesto: sin ella se responde sobre
 * la base, porque un total que sumara monedas distintas no sería ninguna cantidad. Solo se
 * suman juntas cuando el cliente lo pide con una moneda de destino, y entonces cada importe
 * se convierte antes de sumarse.
 */
@RestController
@RequiredArgsConstructor
public class TransactionSummaryController implements SummaryApi {

  private final TransactionSummaryService transactionSummaryService;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<TransactionSummaryResponse>> getTransactionSummary(
      Integer month, Integer year, LocalDateTime dateFrom, LocalDateTime dateTo,
      Long transactionTypeId, Long categoryId, String tag, String search, Currency currency,
      Currency convertTo, BigDecimal rate, ServerWebExchange exchange) {
    return Mono.fromCallable(() -> toCriteria(month, year, dateFrom, dateTo, transactionTypeId,
            categoryId, tag, search, currency, convertTo, rate))
        .flatMap(criteria -> authenticatedUser.currentUserId()
            .flatMap(userId -> transactionSummaryService.summarize(userId, criteria)))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<SummarySeriesResponse>> getTransactionSummarySeries(
      Integer month, Integer year, LocalDateTime dateFrom, LocalDateTime dateTo,
      Long transactionTypeId, Long categoryId, String tag, String search, Currency currency,
      Currency convertTo, BigDecimal rate, SummaryGranularity granularity,
      ServerWebExchange exchange) {
    return Mono.fromCallable(() -> toCriteria(month, year, dateFrom, dateTo, transactionTypeId,
            categoryId, tag, search, currency, convertTo, rate))
        .flatMap(criteria -> authenticatedUser.currentUserId()
            .flatMap(userId -> transactionSummaryService.summarizeSeries(userId, criteria,
                granularity)))
        .map(ResponseEntity::ok);
  }

  /**
   * Arma los criterios del resumen a partir de los parámetros de la petición.
   *
   * <p>Sin conversión, los totales son de una sola moneda: la pedida o la base. Con ella,
   * el filtro de moneda deja de acotar, porque lo que se pide es justo sumarlas todas, y el
   * tipo de referencia se resuelve según la moneda de destino.</p>
   *
   * @param month             mes de la transacción
   * @param year              año de la transacción
   * @param dateFrom          fecha inicial inclusiva
   * @param dateTo            fecha final inclusiva
   * @param transactionTypeId identificador del tipo de transacción
   * @param categoryId        identificador de la categoría principal
   * @param tag               nombre del tag asociado
   * @param search            texto a buscar
   * @param currency          moneda a la que acotar, puede ser nula
   * @param convertTo         moneda a la que convertir, nula para no convertir
   * @param rate              tipo de referencia de la conversión, puede ser nulo
   * @return los criterios del resumen
   */
  private static TransactionSummaryCriteria toCriteria(Integer month, Integer year,
                                                       LocalDateTime dateFrom,
                                                       LocalDateTime dateTo,
                                                       Long transactionTypeId, Long categoryId,
                                                       String tag, String search,
                                                       Currency currency, Currency convertTo,
                                                       BigDecimal rate) {
    if (convertTo == null) {
      return new TransactionSummaryCriteria(month, year, dateFrom, dateTo, transactionTypeId,
          categoryId, tag, search, CurrencyRules.orBase(currency).getValue());
    }
    return new TransactionSummaryCriteria(month, year, dateFrom, dateTo, transactionTypeId,
        categoryId, tag, search, null, convertTo.getValue(),
        CurrencyRules.conversionRate(convertTo, rate));
  }
}
