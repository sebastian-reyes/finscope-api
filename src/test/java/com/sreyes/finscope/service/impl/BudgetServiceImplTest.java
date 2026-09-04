package com.sreyes.finscope.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.exception.custom.BudgetAlreadySetException;
import com.sreyes.finscope.exception.custom.BudgetNotFoundException;
import com.sreyes.finscope.exception.custom.CategoryNotApplicableException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.DateNotFoundException;
import com.sreyes.finscope.model.entity.Budget;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.query.BudgetProgress;
import com.sreyes.finscope.repository.BudgetRepository;
import com.sreyes.finscope.repository.CategoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link BudgetServiceImpl}, centradas en las tres cosas que pueden
 * salir mal sin que se note: que el mes se traduzca a un rango distinto del que usan los
 * resúmenes, que fijar un presupuesto pise en silencio el que ya había, y que se pueda
 * presupuestar una categoría contra la que el avance nunca se movería.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BudgetServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final Long CATEGORY_ID = 4L;
  private static final Long BUDGET_ID = 11L;
  private static final Integer MONTH = 8;
  private static final Integer YEAR = 2026;

  /** Primer instante de agosto de 2026, como lo resuelve el filtro de mes del listado. */
  private static final LocalDateTime PERIOD_START = LocalDateTime.of(2026, 8, 1, 0, 0);

  /** Último instante del mismo mes: el rango de la API es inclusivo por los dos extremos. */
  private static final LocalDateTime PERIOD_END =
      LocalDateTime.of(2026, 8, 31, 0, 0).with(LocalTime.MAX);

  @Mock
  private BudgetRepository budgetRepository;

  @Mock
  private CategoryRepository categoryRepository;

  @InjectMocks
  private BudgetServiceImpl budgetService;

  /** Categoría de egresos, la que tiene sentido presupuestar. */
  private Category comida() {
    return new Category(CATEGORY_ID, USER_ID, "Comida", "EXPENSE", false);
  }

  /** Categoría de solo ingresos: nada que gastar contra lo que medirse. */
  private Category salario() {
    return new Category(20L, USER_ID, "Salario", "INCOME", false);
  }

  private Budget budget() {
    return new Budget(BUDGET_ID, USER_ID, CATEGORY_ID, MONTH, YEAR, new BigDecimal("400.00"));
  }

  private BudgetProgress progress(String amount, String spent) {
    return progress(amount, spent, "0.00");
  }

  private BudgetProgress progress(String amount, String spent, String committed) {
    return new BudgetProgress(BUDGET_ID, CATEGORY_ID, "Comida", MONTH, YEAR,
        new BigDecimal(amount), new BigDecimal(spent), new BigDecimal(committed));
  }

  @Test
  @DisplayName("Consulta el mes con el mismo rango de fechas que usan los resúmenes")
  void listsBudgetsForTheNaturalMonth() {
    when(budgetRepository.findProgressByPeriod(eq(USER_ID), eq(MONTH), eq(YEAR), any(), any()))
        .thenReturn(Flux.just(progress("400.00", "340.00")));

    StepVerifier.create(budgetService.findBudgets(USER_ID, MONTH, YEAR))
        .assertNext(found -> assertEquals("Comida", found.budgetCategoryName()))
        .verifyComplete();

    ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
    ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(budgetRepository).findProgressByPeriod(eq(USER_ID), eq(MONTH), eq(YEAR),
        from.capture(), to.capture());
    assertEquals(PERIOD_START, from.getValue());
    assertEquals(PERIOD_END, to.getValue());
  }

  @Test
  @DisplayName("Rechaza un mes imposible antes de consultar nada")
  void rejectsImpossibleMonth() {
    StepVerifier.create(budgetService.findBudgets(USER_ID, 13, YEAR))
        .expectError(DateNotFoundException.class)
        .verify();

    verify(budgetRepository, never()).findProgressByPeriod(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Fija el presupuesto de una categoría de egresos y devuelve su avance")
  void createsBudget() {
    when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
        .thenReturn(Mono.just(comida()));
    when(budgetRepository.insertIfAbsent(USER_ID, CATEGORY_ID, MONTH, YEAR,
        new BigDecimal("400.00"))).thenReturn(Mono.just(1L));
    when(budgetRepository.findByCategoryAndPeriod(USER_ID, CATEGORY_ID, MONTH, YEAR))
        .thenReturn(Mono.just(budget()));
    when(budgetRepository.findProgressById(eq(USER_ID), eq(BUDGET_ID), any(), any(), any(), any()))
        .thenReturn(Mono.just(progress("400.00", "0.00")));

    StepVerifier.create(budgetService.createBudget(USER_ID, CATEGORY_ID, MONTH, YEAR,
        new BigDecimal("400.00")))
        .assertNext(created -> {
          assertEquals(new BigDecimal("400.00"), created.budgetAmount());
          assertEquals(new BigDecimal("0.00"), created.budgetSpent());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Rechaza presupuestar una categoría de solo ingresos")
  void rejectsIncomeOnlyCategory() {
    when(categoryRepository.findByIdAndUserId(20L, USER_ID)).thenReturn(Mono.just(salario()));

    StepVerifier.create(budgetService.createBudget(USER_ID, 20L, MONTH, YEAR,
        new BigDecimal("400.00")))
        .expectError(CategoryNotApplicableException.class)
        .verify();

    verify(budgetRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Una categoría de otra cuenta se comporta como inexistente")
  void rejectsUnknownCategory() {
    when(categoryRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(budgetService.createBudget(USER_ID, 99L, MONTH, YEAR,
        new BigDecimal("400.00")))
        .expectError(CategoryNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Rechaza el segundo presupuesto de la misma categoría en el mismo mes")
  void rejectsDuplicatedBudget() {
    when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
        .thenReturn(Mono.just(comida()));
    // Ninguna fila escrita: la restricción de unicidad ya tenía ese par categoría-mes.
    when(budgetRepository.insertIfAbsent(USER_ID, CATEGORY_ID, MONTH, YEAR,
        new BigDecimal("500.00"))).thenReturn(Mono.just(0L));

    StepVerifier.create(budgetService.createBudget(USER_ID, CATEGORY_ID, MONTH, YEAR,
        new BigDecimal("500.00")))
        .expectError(BudgetAlreadySetException.class)
        .verify();

    verify(budgetRepository, never()).findProgressById(any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("Cambia el importe y recalcula el avance sobre el mes del presupuesto")
  void updatesAmount() {
    when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Mono.just(budget()));
    when(budgetRepository.save(any(Budget.class))).thenAnswer(call -> Mono.just(call
        .getArgument(0)));
    when(budgetRepository.findProgressById(eq(USER_ID), eq(BUDGET_ID), any(), any(), any(), any()))
        .thenReturn(Mono.just(progress("500.00", "340.00")));

    StepVerifier.create(budgetService.updateBudget(USER_ID, BUDGET_ID, new BigDecimal("500.00")))
        .assertNext(updated -> assertEquals(new BigDecimal("500.00"), updated.budgetAmount()))
        .verifyComplete();

    ArgumentCaptor<Budget> saved = ArgumentCaptor.forClass(Budget.class);
    verify(budgetRepository).save(saved.capture());
    assertEquals(new BigDecimal("500.00"), saved.getValue().getAmount());
    // La categoría y el mes identifican al presupuesto: cambiar el importe no los mueve.
    assertEquals(CATEGORY_ID, saved.getValue().getCategoryId());
    assertEquals(MONTH, saved.getValue().getMonth());
  }

  @Test
  @DisplayName("Un presupuesto de otra cuenta no se puede tocar")
  void rejectsUnknownBudget() {
    when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(budgetService.updateBudget(USER_ID, BUDGET_ID, new BigDecimal("500.00")))
        .expectError(BudgetNotFoundException.class)
        .verify();

    verify(budgetRepository, never()).save(any(Budget.class));
  }

  @Test
  @DisplayName("Borrar el presupuesto retira el plan y deja los movimientos donde estaban")
  void deletesBudget() {
    Budget budget = budget();
    when(budgetRepository.findByIdAndUserId(BUDGET_ID, USER_ID)).thenReturn(Mono.just(budget));
    when(budgetRepository.delete(budget)).thenReturn(Mono.empty());

    StepVerifier.create(budgetService.deleteBudget(USER_ID, BUDGET_ID)).verifyComplete();

    verify(budgetRepository).delete(budget);
  }

  @Test
  @DisplayName("Copiar un mes devuelve el destino entero, no solo lo copiado")
  void copiesPreviousMonth() {
    when(budgetRepository.copyPeriod(USER_ID, 7, YEAR, MONTH, YEAR)).thenReturn(Mono.just(3L));
    when(budgetRepository.findProgressByPeriod(eq(USER_ID), eq(MONTH), eq(YEAR), any(), any()))
        .thenReturn(Flux.just(progress("400.00", "0.00"),
            new BudgetProgress(12L, 5L, "Transporte", MONTH, YEAR, new BigDecimal("150.00"),
                new BigDecimal("20.00"), new BigDecimal("0.00"))));

    StepVerifier.create(budgetService.copyBudgets(USER_ID, 7, YEAR, MONTH, YEAR))
        .expectNextCount(2)
        .verifyComplete();

    verify(budgetRepository).copyPeriod(USER_ID, 7, YEAR, MONTH, YEAR);
  }

  @Test
  @DisplayName("No copia desde un mes imposible")
  void rejectsImpossibleSourceMonth() {
    StepVerifier.create(budgetService.copyBudgets(USER_ID, 0, YEAR, MONTH, YEAR))
        .expectError(DateNotFoundException.class)
        .verify();

    verify(budgetRepository, never()).copyPeriod(any(), any(), any(), any(), any());
  }
}
