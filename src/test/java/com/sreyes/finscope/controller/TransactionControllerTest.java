package com.sreyes.finscope.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.api.model.TransactionPageResponse;
import com.sreyes.finscope.api.model.TransactionResponse;
import com.sreyes.finscope.api.model.CategoryResponse;
import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.api.model.TransactionTypeResponse;
import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.exception.custom.DateNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.query.TransactionSearchCriteria;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TransactionCommandService;
import com.sreyes.finscope.service.TransactionQueryService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
 * Pruebas del contrato HTTP expuesto por {@link TransactionController}, incluidos los filtros,
 * la paginación y la representación de los tags de una transacción. El usuario autenticado se
 * simula para poder ejercitar el controlador sin emitir tokens reales.
 */
@WebFluxTest(TransactionController.class)
@Import(TimeConfig.class)
class TransactionControllerTest {

  private static final Long USER_ID = 7L;

  @Autowired
  private WebTestClient webTestClient;

  @MockitoBean
  private TransactionQueryService transactionQueryService;

  @MockitoBean
  private TransactionCommandService transactionCommandService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutateWith(mockUser()).mutateWith(csrf());
    when(authenticatedUser.currentUserId()).thenReturn(Mono.just(USER_ID));
  }

  /**
   * Construye la representación de un egreso con dos tags.
   *
   * @return la representación de la transacción
   */
  private TransactionResponse transactionResponse() {
    TransactionResponse response = new TransactionResponse();
    response.setId(1L);
    response.setAmount(new BigDecimal("300.00"));
    response.setDescription("Videojuego");
    response.setDate(LocalDateTime.of(2026, 8, 17, 20, 0));
    response.setTransactionType(new TransactionTypeResponse(2L, "Egreso",
        TransactionTypeResponse.CodeEnum.EXPENSE));
    response.setCategory(new CategoryResponse(4L, "Entretenimiento", CategoryScope.EXPENSE,
        false, 0L));
    response.setTags(List.of("ocio", "personal"));
    return response;
  }

  /**
   * Construye la entidad que devuelve el servicio de comandos tras persistir.
   *
   * @return la transacción persistida
   */
  private Transaction savedTransaction() {
    return new Transaction(1L, new BigDecimal("300.00"), "Videojuego",
        LocalDateTime.of(2026, 8, 17, 20, 0), USER_ID, 2L, 4L, null);
  }

  @Test
  @DisplayName("Devuelve la página de transacciones con sus metadatos")
  void listsTransactions() {
    when(transactionQueryService.searchTransactions(eq(USER_ID), any()))
        .thenReturn(Mono.just(new TransactionPageResponse(List.of(transactionResponse()),
            0, 20, 1L, 1)));

    webTestClient.get().uri("/transactions")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.page").isEqualTo(0)
        .jsonPath("$.size").isEqualTo(20)
        .jsonPath("$.totalElements").isEqualTo(1)
        .jsonPath("$.totalPages").isEqualTo(1)
        .jsonPath("$.content[0].transactionType.code").isEqualTo("EXPENSE")
        .jsonPath("$.content[0].tags.length()").isEqualTo(2);
  }

  @Test
  @DisplayName("Traslada al servicio todos los filtros recibidos por query string")
  void forwardsAllFilters() {
    when(transactionQueryService.searchTransactions(eq(USER_ID), any()))
        .thenReturn(Mono.just(new TransactionPageResponse(List.of(), 0, 20, 0L, 0)));

    webTestClient.get().uri(uriBuilder -> uriBuilder.path("/transactions")
            .queryParam("month", 8)
            .queryParam("year", 2026)
            .queryParam("transactionTypeId", 2)
            .queryParam("tag", "ocio")
            .queryParam("page", 2)
            .queryParam("size", 5)
            .queryParam("sort", "amount,asc")
            .build())
        .exchange()
        .expectStatus().isOk();

    ArgumentCaptor<TransactionSearchCriteria> captor =
        ArgumentCaptor.forClass(TransactionSearchCriteria.class);
    verify(transactionQueryService).searchTransactions(eq(USER_ID), captor.capture());
    TransactionSearchCriteria criteria = captor.getValue();
    assertEquals(8, criteria.month());
    assertEquals(2026, criteria.year());
    assertEquals(2L, criteria.transactionTypeId());
    assertEquals("ocio", criteria.tag());
    assertEquals(2, criteria.page());
    assertEquals(5, criteria.size());
    assertEquals("amount,asc", criteria.sort());
  }

  @Test
  @DisplayName("Aplica los valores por defecto de paginación y ordenamiento")
  void appliesPaginationDefaults() {
    when(transactionQueryService.searchTransactions(eq(USER_ID), any()))
        .thenReturn(Mono.just(new TransactionPageResponse(List.of(), 0, 20, 0L, 0)));

    webTestClient.get().uri("/transactions").exchange().expectStatus().isOk();

    ArgumentCaptor<TransactionSearchCriteria> captor =
        ArgumentCaptor.forClass(TransactionSearchCriteria.class);
    verify(transactionQueryService).searchTransactions(eq(USER_ID), captor.capture());
    assertEquals(0, captor.getValue().page());
    assertEquals(20, captor.getValue().size());
    assertEquals("date,desc", captor.getValue().sort());
  }

  @Test
  @DisplayName("Rechaza con 400 un mes fuera de rango")
  void rejectsInvalidMonth() {
    webTestClient.get().uri("/transactions?month=13")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Traduce el conflicto entre filtros de fecha en un 400")
  void translatesConflictingDateFilters() {
    when(transactionQueryService.searchTransactions(eq(USER_ID), any()))
        .thenReturn(Mono.error(new DateNotFoundException("conflict")));

    webTestClient.get().uri("/transactions?month=8&year=2026&dateFrom=2026-01-01T00:00:00")
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.code").isEqualTo("INVALID_DATE_FILTER");
  }

  @Test
  @DisplayName("Devuelve una transacción por su identificador")
  void returnsTransactionById() {
    when(transactionQueryService.getTransactionById(USER_ID, 1L))
        .thenReturn(Mono.just(transactionResponse()));

    webTestClient.get().uri("/transactions/1")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.amount").isEqualTo(300.00)
        .jsonPath("$.tags[0]").isEqualTo("ocio");
  }

  @Test
  @DisplayName("Traduce la transacción inexistente en un 404")
  void translatesNotFound() {
    when(transactionQueryService.getTransactionById(USER_ID, 99L))
        .thenReturn(Mono.error(new TransactionNotFoundException("Transaction not found with id: 99")));

    webTestClient.get().uri("/transactions/99")
        .exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.code").isEqualTo("TRANSACTION_NOT_FOUND");
  }

  @Test
  @DisplayName("Crea una transacción con tags y responde 201")
  void createsTransactionWithTags() {
    when(transactionCommandService.createTransaction(eq(USER_ID), any()))
        .thenReturn(Mono.just(savedTransaction()));
    when(transactionQueryService.getTransactionById(USER_ID, 1L))
        .thenReturn(Mono.just(transactionResponse()));

    webTestClient.post().uri("/transactions")
        .bodyValue(Map.of("amount", 300.00, "transactionTypeId", 2, "categoryId", 4,
            "tags", List.of("ocio", "personal")))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.tags.length()").isEqualTo(2);
  }

  @Test
  @DisplayName("Rechaza con 400 una transacción con importe no positivo")
  void rejectsNonPositiveAmount() {
    webTestClient.post().uri("/transactions")
        .bodyValue(Map.of("amount", 0, "transactionTypeId", 2, "categoryId", 4))
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
  }

  @Test
  @DisplayName("Rechaza con 400 una transacción sin tipo")
  void rejectsMissingTransactionType() {
    webTestClient.post().uri("/transactions")
        .bodyValue(Map.of("amount", 10))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Traduce en 404 la referencia a un tipo de transacción inexistente")
  void translatesMissingTransactionTypeReference() {
    when(transactionCommandService.createTransaction(eq(USER_ID), any()))
        .thenReturn(Mono.error(
            new TransactionTypeNotFoundException("Transaction type not found with id: 99")));

    webTestClient.post().uri("/transactions")
        .bodyValue(Map.of("amount", 10, "transactionTypeId", 99, "categoryId", 4))
        .exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.code").isEqualTo("TRANSACTION_TYPE_NOT_FOUND");
  }

  @Test
  @DisplayName("Actualiza los tags de una transacción")
  void updatesTransactionTags() {
    when(transactionCommandService.updateTransaction(eq(USER_ID), anyLong(), any()))
        .thenReturn(Mono.just(savedTransaction()));
    when(transactionQueryService.getTransactionById(USER_ID, 1L))
        .thenReturn(Mono.just(transactionResponse()));

    webTestClient.patch().uri("/transactions/1")
        .bodyValue(Map.of("tags", List.of("ocio", "personal")))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.tags.length()").isEqualTo(2);
  }

  @Test
  @DisplayName("Responde 204 al eliminar una transacción")
  void deletesTransaction() {
    when(transactionCommandService.deleteTransactionById(USER_ID, 1L)).thenReturn(Mono.empty());

    webTestClient.delete().uri("/transactions/1")
        .exchange()
        .expectStatus().isNoContent();
  }
}
