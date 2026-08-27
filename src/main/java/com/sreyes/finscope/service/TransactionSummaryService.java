package com.sreyes.finscope.service;

import com.sreyes.finscope.api.model.SummaryGranularity;
import com.sreyes.finscope.api.model.SummarySeriesResponse;
import com.sreyes.finscope.api.model.TransactionSummaryResponse;
import com.sreyes.finscope.model.query.TransactionSummaryCriteria;
import reactor.core.publisher.Mono;

/**
 * Servicio de consulta de agregados financieros.
 * Vive separado de {@link TransactionQueryService} porque responde a otra pregunta: aquel
 * devuelve transacciones y este devuelve cuánto suman, sin que el cliente tenga que
 * traérselas para contarlas.
 */
public interface TransactionSummaryService {

  /**
   * Resume los ingresos y egresos que cumplen los filtros, con su desglose por tag.
   *
   * @param userId   identificador del usuario propietario
   * @param criteria filtros solicitados
   * @return el resumen del periodo
   */
  Mono<TransactionSummaryResponse> summarize(Long userId, TransactionSummaryCriteria criteria);

  /**
   * Resume los ingresos y egresos agrupados en tramos consecutivos de tiempo.
   *
   * @param userId      identificador del usuario propietario
   * @param criteria    filtros solicitados
   * @param granularity tamaño de cada tramo
   * @return la serie temporal del periodo
   */
  Mono<SummarySeriesResponse> summarizeSeries(Long userId, TransactionSummaryCriteria criteria,
                                              SummaryGranularity granularity);
}
