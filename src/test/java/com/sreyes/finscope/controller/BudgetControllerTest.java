package com.sreyes.finscope.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.exception.custom.BudgetAlreadySetException;
import com.sreyes.finscope.exception.custom.BudgetNotFoundException;
import com.sreyes.finscope.exception.custom.CategoryNotApplicableException;
import com.sreyes.finscope.model.query.BudgetProgress;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.BudgetService;
import com.sreyes.finscope.util.mapper.BudgetMapperImpl;
import java.math.BigDecimal;
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
 * Pruebas del contrato HTTP expuesto por {@link BudgetController}. El mapper real se importa
 * en lugar de simularse porque justo lo que se comprueba aquí es la forma del JSON que sale
 * de él: sobre todo `remaining`, que no viene de la base de datos sino de la resta, y que es
 * el número contra el que el usuario decide si le queda margen.
 */
@WebFluxTest(BudgetController.class)
@Import({TimeConfig.class, BudgetMapperImpl.class})
class BudgetControllerTest {

  private static final Long USER_ID = 7L;
  private static final Long BUDGET_ID = 11L;
  private static final Long CATEGORY_ID = 4L;

  @Autowired
  private WebTestClient webTestClient;

  @MockitoBean
  private BudgetService budgetService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutateWith(mockUser()).mutateWith(csrf());
    when(authenticatedUser.currentUserId()).thenReturn(Mono.just(USER_ID));
  }

  private BudgetProgress progress(String amount, String spent) {
    return progress(amount, spent, "0.00");
  }

  private BudgetProgress progress(String amount, String spent, String committed) {
    return new BudgetProgress(BUDGET_ID, CATEGORY_ID, "Comida", 8, 2026, new BigDecimal(amount),
        new BigDecimal(spent), new BigDecimal(committed));
  }

  @Test
  @DisplayName("Devuelve los presupuestos del mes con lo gastado y lo que queda")
  void listsBudgetsWithProgress() {
    when(budgetService.findBudgets(USER_ID, 8, 2026)).thenReturn(Flux.just(
        progress("400.00", "340.00"),
        new BudgetProgress(12L, 5L, "Transporte", 8, 2026, new BigDecimal("150.00"),
            new BigDecimal("20.00"), new BigDecimal("0.00"))));

    webTestClient.get().uri("/budgets?month=8&year=2026")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$[0].id").isEqualTo(11)
        .jsonPath("$[0].categoryId").isEqualTo(4)
        .jsonPath("$[0].category").isEqualTo("Comida")
        .jsonPath("$[0].month").isEqualTo(8)
        .jsonPath("$[0].year").isEqualTo(2026)
        .jsonPath("$[0].amount").isEqualTo(400.00)
        .jsonPath("$[0].spent").isEqualTo(340.00)
        .jsonPath("$[0].remaining").isEqualTo(60.00)
        .jsonPath("$[1].remaining").isEqualTo(130.00);

    verify(budgetService).findBudgets(USER_ID, 8, 2026);
  }

  @Test
  @DisplayName("Lo que queda sale negativo cuando el gasto se pasó del límite")
  void reportsOverspending() {
    when(budgetService.findBudgets(USER_ID, 8, 2026))
        .thenReturn(Flux.just(progress("400.00", "455.50")));

    webTestClient.get().uri("/budgets?month=8&year=2026")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].remaining").isEqualTo(-55.50);
  }

  @Test
  @DisplayName("Lo disponible descuenta los fijos que aún no se han pagado")
  void discountsCommittedRecurring() {
    when(budgetService.findBudgets(USER_ID, 8, 2026))
        .thenReturn(Flux.just(progress("400.00", "120.00", "180.00")));

    webTestClient.get().uri("/budgets?month=8&year=2026")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[0].spent").isEqualTo(120.00)
        .jsonPath("$[0].committed").isEqualTo(180.00)
        // Lo que queda mira solo al pasado; lo disponible es con lo que se decide hoy.
        .jsonPath("$[0].remaining").isEqualTo(280.00)
        .jsonPath("$[0].available").isEqualTo(100.00);
  }

  @Test
  @DisplayName("El mes y el año son obligatorios para pedir presupuestos")
  void requiresPeriod() {
    webTestClient.get().uri("/budgets")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Fija un presupuesto y responde 201 con su avance a cero")
  void createsBudget() {
    when(budgetService.createBudget(eq(USER_ID), eq(CATEGORY_ID), eq(8), eq(2026), any()))
        .thenReturn(Mono.just(progress("400.00", "0.00")));

    webTestClient.post().uri("/budgets")
        .bodyValue(Map.of("categoryId", 4, "month", 8, "year", 2026, "amount", 400.00))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.amount").isEqualTo(400.00)
        .jsonPath("$.spent").isEqualTo(0.00)
        .jsonPath("$.remaining").isEqualTo(400.00);
  }

  @Test
  @DisplayName("Presupuestar dos veces la misma categoría en el mismo mes da conflicto")
  void rejectsDuplicatedBudget() {
    when(budgetService.createBudget(eq(USER_ID), eq(CATEGORY_ID), eq(8), eq(2026), any()))
        .thenReturn(Mono.error(new BudgetAlreadySetException("ya presupuestada")));

    webTestClient.post().uri("/budgets")
        .bodyValue(Map.of("categoryId", 4, "month", 8, "year", 2026, "amount", 400.00))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.code").isEqualTo("BUDGET_ALREADY_SET");
  }

  @Test
  @DisplayName("Una categoría de solo ingresos no se puede presupuestar")
  void rejectsIncomeOnlyCategory() {
    when(budgetService.createBudget(eq(USER_ID), eq(20L), eq(8), eq(2026), any()))
        .thenReturn(Mono.error(new CategoryNotApplicableException("no admite egresos")));

    webTestClient.post().uri("/budgets")
        .bodyValue(Map.of("categoryId", 20, "month", 8, "year", 2026, "amount", 400.00))
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.code").isEqualTo("CATEGORY_NOT_APPLICABLE");
  }

  @Test
  @DisplayName("Rechaza un importe que no es mayor que cero")
  void rejectsNonPositiveAmount() {
    webTestClient.post().uri("/budgets")
        .bodyValue(Map.of("categoryId", 4, "month", 8, "year", 2026, "amount", 0))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Cambia el importe del presupuesto")
  void updatesBudget() {
    when(budgetService.updateBudget(eq(USER_ID), eq(BUDGET_ID), any()))
        .thenReturn(Mono.just(progress("500.00", "340.00")));

    webTestClient.patch().uri("/budgets/11")
        .bodyValue(Map.of("amount", 500.00))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.amount").isEqualTo(500.00)
        .jsonPath("$.remaining").isEqualTo(160.00);
  }

  @Test
  @DisplayName("Un presupuesto que no existe responde 404")
  void reportsMissingBudget() {
    when(budgetService.updateBudget(eq(USER_ID), eq(BUDGET_ID), any()))
        .thenReturn(Mono.error(new BudgetNotFoundException("no existe")));

    webTestClient.patch().uri("/budgets/11")
        .bodyValue(Map.of("amount", 500.00))
        .exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.code").isEqualTo("BUDGET_NOT_FOUND");
  }

  @Test
  @DisplayName("Retira el presupuesto y responde 204")
  void deletesBudget() {
    when(budgetService.deleteBudget(USER_ID, BUDGET_ID)).thenReturn(Mono.empty());

    webTestClient.delete().uri("/budgets/11")
        .exchange()
        .expectStatus().isNoContent();

    verify(budgetService).deleteBudget(USER_ID, BUDGET_ID);
  }

  @Test
  @DisplayName("Copiar un mes devuelve el destino tal y como queda")
  void copiesBudgets() {
    when(budgetService.copyBudgets(USER_ID, 7, 2026, 8, 2026))
        .thenReturn(Flux.just(progress("400.00", "0.00")));

    webTestClient.post().uri("/budgets/copy")
        .bodyValue(Map.of("sourceMonth", 7, "sourceYear", 2026, "month", 8, "year", 2026))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].category").isEqualTo("Comida")
        .jsonPath("$[0].spent").isEqualTo(0.00);

    verify(budgetService).copyBudgets(USER_ID, 7, 2026, 8, 2026);
  }
}
