package com.sreyes.finscope.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.exception.custom.RecurringAlreadyConfirmedException;
import com.sreyes.finscope.exception.custom.RecurringNotDueException;
import com.sreyes.finscope.exception.custom.RecurringNotFoundException;
import com.sreyes.finscope.model.entity.RecurringTransaction;
import com.sreyes.finscope.model.query.RecurringDetail;
import com.sreyes.finscope.model.query.RecurringOccurrence;
import com.sreyes.finscope.model.query.RecurringState;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.RecurringTransactionService;
import com.sreyes.finscope.util.mapper.RecurringTransactionMapperImpl;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Pruebas del contrato HTTP expuesto por {@link RecurringController}. El mapper real se
 * importa en lugar de simularse porque lo que se comprueba aquí es la forma del JSON que
 * sale de él: el estado y el día de vencimiento no vienen de ninguna tabla y son justo lo
 * que la pantalla usa para decidir qué pinta en rojo.
 */
@WebFluxTest(RecurringController.class)
@Import({TimeConfig.class, RecurringTransactionMapperImpl.class})
class RecurringControllerTest {

  private static final Long USER_ID = 7L;
  private static final Long RECURRING_ID = 11L;
  private static final Long CATEGORY_ID = 4L;
  private static final Long TYPE_ID = 2L;

  @Autowired
  private WebTestClient webTestClient;

  @MockitoBean
  private RecurringTransactionService recurringTransactionService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutateWith(mockUser()).mutateWith(csrf());
    when(authenticatedUser.currentUserId()).thenReturn(Mono.just(USER_ID));
  }

  private RecurringDetail detail(Long transactionId) {
    return new RecurringDetail(RECURRING_ID, CATEGORY_ID, "Servicios", TYPE_ID, "EXPENSE",
        "Internet", new BigDecimal("180.00"), 12, 1, 1, 2026, true, 8, 2026, true, false,
        transactionId,
        transactionId == null ? null : new BigDecimal("175.50"),
        transactionId == null ? null : LocalDateTime.of(2026, 8, 12, 9, 0));
  }

  private RecurringOccurrence occurrence(RecurringState state, Long transactionId) {
    return new RecurringOccurrence(detail(transactionId), LocalDate.of(2026, 8, 12), state);
  }

  private RecurringTransaction template() {
    return new RecurringTransaction(RECURRING_ID, USER_ID, CATEGORY_ID, TYPE_ID, "Internet",
        new BigDecimal("180.00"), 12, 1, 8, 2026, true);
  }

  @Test
  @DisplayName("Devuelve los fijos del mes con su estado y su día de vencimiento")
  void listsRecurringWithState() {
    when(recurringTransactionService.findRecurring(USER_ID, 8, 2026))
        .thenReturn(Flux.just(occurrence(RecurringState.OVERDUE, null)));

    webTestClient.get().uri("/recurring-transactions?month=8&year=2026")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].id").isEqualTo(11)
        .jsonPath("$[0].categoryId").isEqualTo(4)
        .jsonPath("$[0].category").isEqualTo("Servicios")
        .jsonPath("$[0].type").isEqualTo("EXPENSE")
        .jsonPath("$[0].description").isEqualTo("Internet")
        .jsonPath("$[0].amount").isEqualTo(180.00)
        .jsonPath("$[0].dayOfMonth").isEqualTo(12)
        .jsonPath("$[0].month").isEqualTo(8)
        .jsonPath("$[0].year").isEqualTo(2026)
        .jsonPath("$[0].dueDate").isEqualTo("2026-08-12")
        .jsonPath("$[0].status").isEqualTo("OVERDUE")
        .jsonPath("$[0].transactionId").doesNotExist();

    verify(recurringTransactionService).findRecurring(USER_ID, 8, 2026);
  }

  @Test
  @DisplayName("Un fijo pagado lleva el movimiento con el que se confirmó y su importe real")
  void exposesWhatWasActuallyPaid() {
    when(recurringTransactionService.findRecurring(USER_ID, 8, 2026))
        .thenReturn(Flux.just(occurrence(RecurringState.PAID, 99L)));

    webTestClient.get().uri("/recurring-transactions?month=8&year=2026")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].status").isEqualTo("PAID")
        .jsonPath("$[0].transactionId").isEqualTo(99)
        // Lo pagado puede no ser lo estimado, y esa diferencia es justo lo que interesa ver.
        .jsonPath("$[0].amount").isEqualTo(180.00)
        .jsonPath("$[0].paidAmount").isEqualTo(175.50);
  }

  @Test
  @DisplayName("El mes y el año son obligatorios para pedir los fijos")
  void requiresPeriod() {
    webTestClient.get().uri("/recurring-transactions")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Da de alta un fijo y responde 201 con la plantilla")
  void createsRecurring() {
    when(recurringTransactionService.createRecurring(eq(USER_ID), any()))
        .thenReturn(Mono.just(template()));

    webTestClient.post().uri("/recurring-transactions")
        .bodyValue(Map.of("categoryId", 4, "transactionTypeId", 2, "description", "Internet",
            "amount", 180.00, "dayOfMonth", 12, "startMonth", 8, "startYear", 2026))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo(11)
        .jsonPath("$.description").isEqualTo("Internet")
        .jsonPath("$.everyMonths").isEqualTo(1)
        .jsonPath("$.active").isEqualTo(true)
        // La plantilla no mira ningún mes, así que no lleva estado.
        .jsonPath("$.status").doesNotExist();
  }

  @Test
  @DisplayName("Rechaza un importe que no es mayor que cero")
  void rejectsNonPositiveAmount() {
    webTestClient.post().uri("/recurring-transactions")
        .bodyValue(Map.of("categoryId", 4, "transactionTypeId", 2, "description", "Internet",
            "amount", 0, "dayOfMonth", 12, "startMonth", 8, "startYear", 2026))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Rechaza un día que no existe en ningún mes")
  void rejectsAnImpossibleDay() {
    webTestClient.post().uri("/recurring-transactions")
        .bodyValue(Map.of("categoryId", 4, "transactionTypeId", 2, "description", "Internet",
            "amount", 180.00, "dayOfMonth", 32, "startMonth", 8, "startYear", 2026))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Confirmar un mes responde 201 con el fijo ya como pagado")
  void confirmsAMonth() {
    when(recurringTransactionService.confirmRecurring(eq(USER_ID), eq(RECURRING_ID), any()))
        .thenReturn(Mono.just(occurrence(RecurringState.PAID, 99L)));

    webTestClient.post().uri("/recurring-transactions/11/confirm")
        .bodyValue(Map.of("month", 8, "year", 2026))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.status").isEqualTo("PAID")
        .jsonPath("$.transactionId").isEqualTo(99);
  }

  @Test
  @DisplayName("Confirmar dos veces el mismo mes responde conflicto")
  void reportsAlreadyConfirmed() {
    when(recurringTransactionService.confirmRecurring(eq(USER_ID), eq(RECURRING_ID), any()))
        .thenReturn(Mono.error(new RecurringAlreadyConfirmedException("ya confirmado")));

    webTestClient.post().uri("/recurring-transactions/11/confirm")
        .bodyValue(Map.of("month", 8, "year", 2026))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.code").isEqualTo("RECURRING_ALREADY_CONFIRMED");
  }

  @Test
  @DisplayName("Confirmar un mes en el que no vence es un error de la petición")
  void reportsNotDue() {
    when(recurringTransactionService.confirmRecurring(eq(USER_ID), eq(RECURRING_ID), any()))
        .thenReturn(Mono.error(new RecurringNotDueException("no vence")));

    webTestClient.post().uri("/recurring-transactions/11/confirm")
        .bodyValue(Map.of("month", 9, "year", 2026))
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.code").isEqualTo("RECURRING_NOT_DUE");
  }

  @Test
  @DisplayName("Omitir un mes lo deja marcado como omitido")
  void skipsAMonth() {
    when(recurringTransactionService.skipRecurring(USER_ID, RECURRING_ID, 8, 2026))
        .thenReturn(Mono.just(occurrence(RecurringState.SKIPPED, null)));

    webTestClient.post().uri("/recurring-transactions/11/skip")
        .bodyValue(Map.of("month", 8, "year", 2026))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.status").isEqualTo("SKIPPED");
  }

  @Test
  @DisplayName("Deshacer la omisión devuelve el fijo a pendiente")
  void unskipsAMonth() {
    when(recurringTransactionService.unskipRecurring(USER_ID, RECURRING_ID, 8, 2026))
        .thenReturn(Mono.just(occurrence(RecurringState.PENDING, null)));

    webTestClient.delete().uri("/recurring-transactions/11/skip?month=8&year=2026")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.status").isEqualTo("PENDING");
  }

  @Test
  @DisplayName("Elimina un fijo y responde 204")
  void deletesRecurring() {
    when(recurringTransactionService.deleteRecurring(USER_ID, RECURRING_ID))
        .thenReturn(Mono.empty());

    webTestClient.delete().uri("/recurring-transactions/11")
        .exchange()
        .expectStatus().isNoContent();

    verify(recurringTransactionService).deleteRecurring(USER_ID, RECURRING_ID);
  }

  @Test
  @DisplayName("Un fijo de otra cuenta responde 404")
  void hidesRecurringOfOtherUsers() {
    when(recurringTransactionService.deleteRecurring(USER_ID, RECURRING_ID))
        .thenReturn(Mono.error(new RecurringNotFoundException("no existe")));

    webTestClient.delete().uri("/recurring-transactions/11")
        .exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.code").isEqualTo("RECURRING_NOT_FOUND");
  }
}
