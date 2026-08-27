package com.sreyes.finscope.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.api.model.SummaryBucketResponse;
import com.sreyes.finscope.api.model.SummaryGranularity;
import com.sreyes.finscope.api.model.SummarySeriesResponse;
import com.sreyes.finscope.api.model.CategorySummaryResponse;
import com.sreyes.finscope.api.model.TagSummaryResponse;
import com.sreyes.finscope.api.model.TransactionSummaryResponse;
import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.model.query.TransactionSummaryCriteria;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TransactionSummaryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Pruebas del contrato HTTP expuesto por {@link TransactionSummaryController}.
 * Lo que se comprueba aquí no son los cálculos, que son cosa del servicio, sino que los
 * filtros del listado lleguen intactos al agregado y que la granularidad tenga el valor por
 * defecto que promete el contrato.
 */
@WebFluxTest(TransactionSummaryController.class)
@Import(TimeConfig.class)
class TransactionSummaryControllerTest {

  private static final Long USER_ID = 7L;

  @Autowired
  private WebTestClient webTestClient;

  @MockitoBean
  private TransactionSummaryService transactionSummaryService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutateWith(mockUser()).mutateWith(csrf());
    when(authenticatedUser.currentUserId()).thenReturn(Mono.just(USER_ID));
  }

  /**
   * Construye un resumen con una categoría y un tag en los desgloses.
   *
   * @return la representación del resumen
   */
  private TransactionSummaryResponse summary() {
    CategorySummaryResponse byCategory = new CategorySummaryResponse(4L, "Entretenimiento",
        new BigDecimal("0.00"), new BigDecimal("2049.50"), 12L);
    TagSummaryResponse byTag = new TagSummaryResponse(new BigDecimal("0.00"),
        new BigDecimal("120.50"), 3L);
    byTag.setTag("ocio");
    return new TransactionSummaryResponse(new BigDecimal("6200.00"), new BigDecimal("2049.50"),
        new BigDecimal("4150.50"), 12L, List.of(byCategory), List.of(byTag));
  }

  @Test
  @DisplayName("Devuelve los totales del periodo con su desglose por tag")
  void returnsSummary() {
    when(transactionSummaryService.summarize(eq(USER_ID), any(TransactionSummaryCriteria.class)))
        .thenReturn(Mono.just(summary()));

    webTestClient.get().uri("/transactions/summary?month=8&year=2026")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.income").isEqualTo(6200.00)
        .jsonPath("$.expense").isEqualTo(2049.50)
        .jsonPath("$.net").isEqualTo(4150.50)
        .jsonPath("$.transactionCount").isEqualTo(12)
        .jsonPath("$.byTag[0].tag").isEqualTo("ocio");
  }

  @Test
  @DisplayName("Traslada al agregado los mismos filtros que acepta el listado")
  void forwardsEveryFilter() {
    when(transactionSummaryService.summarize(eq(USER_ID), any(TransactionSummaryCriteria.class)))
        .thenReturn(Mono.just(summary()));

    webTestClient.get().uri(uriBuilder -> uriBuilder.path("/transactions/summary")
            .queryParam("dateFrom", "2026-08-01T00:00:00")
            .queryParam("dateTo", "2026-08-31T23:59:59")
            .queryParam("transactionTypeId", 2)
            .queryParam("tag", "ocio")
            .build())
        .exchange()
        .expectStatus().isOk();

    ArgumentCaptor<TransactionSummaryCriteria> criteria =
        ArgumentCaptor.forClass(TransactionSummaryCriteria.class);
    verify(transactionSummaryService).summarize(eq(USER_ID), criteria.capture());

    TransactionSummaryCriteria captured = criteria.getValue();
    org.junit.jupiter.api.Assertions.assertEquals(
        LocalDateTime.parse("2026-08-01T00:00:00"), captured.dateFrom());
    org.junit.jupiter.api.Assertions.assertEquals(
        LocalDateTime.parse("2026-08-31T23:59:59"), captured.dateTo());
    org.junit.jupiter.api.Assertions.assertEquals(2L, captured.transactionTypeId());
    org.junit.jupiter.api.Assertions.assertEquals("ocio", captured.tag());
  }

  @Test
  @DisplayName("Agrupa la serie por meses cuando no se pide granularidad")
  void defaultsToMonthlyBuckets() {
    SummaryBucketResponse bucket = new SummaryBucketResponse(
        LocalDateTime.parse("2026-08-01T00:00:00"), new BigDecimal("6200.00"),
        new BigDecimal("2049.50"), new BigDecimal("4150.50"), 12L);
    when(transactionSummaryService.summarizeSeries(eq(USER_ID),
        any(TransactionSummaryCriteria.class), eq(SummaryGranularity.MONTH)))
        .thenReturn(Mono.just(new SummarySeriesResponse(SummaryGranularity.MONTH,
            List.of(bucket))));

    webTestClient.get().uri("/transactions/summary/series?year=2026")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.granularity").isEqualTo("MONTH")
        .jsonPath("$.buckets.length()").isEqualTo(1)
        .jsonPath("$.buckets[0].net").isEqualTo(4150.50);
  }

  @Test
  @DisplayName("Respeta la granularidad pedida")
  void honoursRequestedGranularity() {
    when(transactionSummaryService.summarizeSeries(eq(USER_ID),
        any(TransactionSummaryCriteria.class), eq(SummaryGranularity.DAY)))
        .thenReturn(Mono.just(new SummarySeriesResponse(SummaryGranularity.DAY, List.of())));

    webTestClient.get().uri("/transactions/summary/series?month=8&year=2026&granularity=DAY")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.granularity").isEqualTo("DAY")
        .jsonPath("$.buckets.length()").isEqualTo(0);
  }

  @Test
  @DisplayName("Rechaza un mes fuera de rango antes de llegar al servicio")
  void rejectsMonthOutOfRange() {
    webTestClient.get().uri("/transactions/summary?month=13")
        .exchange()
        .expectStatus().isBadRequest();
  }
}
