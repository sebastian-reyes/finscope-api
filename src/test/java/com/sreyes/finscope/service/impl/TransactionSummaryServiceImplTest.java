package com.sreyes.finscope.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.api.model.CategorySummaryResponse;
import com.sreyes.finscope.api.model.TransactionSummaryResponse;
import com.sreyes.finscope.model.query.AmountTotal;
import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.model.query.TransactionSummaryCriteria;
import com.sreyes.finscope.repository.TransactionSummaryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link TransactionSummaryServiceImpl}.
 *
 * <p>El escenario es el que motivó separar la categoría del tag. Tres egresos:</p>
 *
 * <pre>
 * Regalo para Gab   80   categoria Regalos   tags: gab
 * Almuerzo con Gab  40   categoria Comida    tags: gab, salida
 * Desayuno          15   categoria Comida    tags: (ninguno)
 * </pre>
 *
 * <p>El desglose por categoría debe sumar exactamente los 135 gastados, mientras que el
 * desglose por tag suma más porque el almuerzo cuenta entero en sus dos tags. Ambos son
 * correctos: solo el primero puede presentarse como un reparto.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransactionSummaryServiceImplTest {

  private static final Long USER_ID = 7L;

  @Mock
  private TransactionSummaryRepository transactionSummaryRepository;

  @InjectMocks
  private TransactionSummaryServiceImpl transactionSummaryService;

  /** Filtros sin acotar, que es como se pide el resumen de todo el historial. */
  private TransactionSummaryCriteria criteria() {
    return new TransactionSummaryCriteria(null, null, null, null, null, null, null);
  }

  /**
   * Configura los tres agregados con el escenario de los 135.
   */
  private void givenTheThreeExpenses() {
    when(transactionSummaryRepository.totalsByType(eq(USER_ID), any(), any(DateRange.class)))
        .thenReturn(Flux.just(
            new AmountTotal("EXPENSE", new BigDecimal("135.00"), 3L, null, null, null, null)));

    when(transactionSummaryRepository.totalsByCategory(eq(USER_ID), any(), any(DateRange.class)))
        .thenReturn(Flux.just(
            new AmountTotal("EXPENSE", new BigDecimal("55.00"), 2L, 1L, "Comida", null, null),
            new AmountTotal("EXPENSE", new BigDecimal("80.00"), 1L, 2L, "Regalos", null, null)));

    when(transactionSummaryRepository.totalsByTag(eq(USER_ID), any(), any(DateRange.class)))
        .thenReturn(Flux.just(
            new AmountTotal("EXPENSE", new BigDecimal("120.00"), 2L, null, null, "gab", null),
            new AmountTotal("EXPENSE", new BigDecimal("40.00"), 1L, null, null, "salida", null),
            new AmountTotal("EXPENSE", new BigDecimal("15.00"), 1L, null, null, null, null)));
  }

  /**
   * Suma los egresos de un desglose por categoría.
   *
   * @param summary resumen del periodo
   * @return la suma de los egresos de todas sus categorías
   */
  private BigDecimal totalByCategory(TransactionSummaryResponse summary) {
    return summary.getByCategory().stream()
        .map(CategorySummaryResponse::getExpense)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  @Test
  @DisplayName("El desglose por categoría suma exactamente el total de egresos del periodo")
  void categoryBreakdownAddsUpToTheExpenseTotal() {
    givenTheThreeExpenses();

    StepVerifier.create(transactionSummaryService.summarize(USER_ID, criteria()))
        .assertNext(summary -> {
          assertEquals(new BigDecimal("135.00"), summary.getExpense());
          assertEquals(new BigDecimal("135.00"), totalByCategory(summary));
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Reparte los 135 entre Regalos y Comida, de mayor a menor egreso")
  void splitsTheExpenseBetweenItsTwoCategories() {
    givenTheThreeExpenses();

    StepVerifier.create(transactionSummaryService.summarize(USER_ID, criteria()))
        .assertNext(summary -> {
          List<CategorySummaryResponse> byCategory = summary.getByCategory();
          assertEquals(2, byCategory.size());
          assertEquals("Regalos", byCategory.get(0).getCategory());
          assertEquals(new BigDecimal("80.00"), byCategory.get(0).getExpense());
          assertEquals("Comida", byCategory.get(1).getCategory());
          assertEquals(new BigDecimal("55.00"), byCategory.get(1).getExpense());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Cada movimiento cuenta una sola vez en el desglose por categoría")
  void countsEachTransactionOnceAcrossCategories() {
    givenTheThreeExpenses();

    StepVerifier.create(transactionSummaryService.summarize(USER_ID, criteria()))
        .assertNext(summary -> {
          long counted = summary.getByCategory().stream()
              .mapToLong(CategorySummaryResponse::getTransactionCount)
              .sum();
          assertEquals(summary.getTransactionCount(), counted);
          assertEquals(3L, counted);
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("El desglose por tag se solapa y por eso no puede repartir el total")
  void tagBreakdownOverlapsAndCannotSplitTheTotal() {
    givenTheThreeExpenses();

    StepVerifier.create(transactionSummaryService.summarize(USER_ID, criteria()))
        .assertNext(summary -> {
          BigDecimal byTag = summary.getByTag().stream()
              .map(tag -> tag.getExpense())
              .reduce(BigDecimal.ZERO, BigDecimal::add);
          // 120 + 40 + 15 = 175 sobre 135 reales: el almuerzo cuenta en gab y en salida.
          assertEquals(new BigDecimal("175.00"), byTag);
          assertNotEquals(summary.getExpense(), byTag);
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Conserva el grupo de las transacciones sin tag")
  void keepsTheUntaggedGroup() {
    givenTheThreeExpenses();

    StepVerifier.create(transactionSummaryService.summarize(USER_ID, criteria()))
        .assertNext(summary -> assertEquals(1, summary.getByTag().stream()
            .filter(tag -> tag.getTag() == null)
            .count()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Calcula el neto como la diferencia entre ingresos y egresos")
  void computesNetBalance() {
    when(transactionSummaryRepository.totalsByType(eq(USER_ID), any(), any(DateRange.class)))
        .thenReturn(Flux.just(
            new AmountTotal("INCOME", new BigDecimal("5000.00"), 1L, null, null, null, null),
            new AmountTotal("EXPENSE", new BigDecimal("135.00"), 3L, null, null, null, null)));
    when(transactionSummaryRepository.totalsByCategory(eq(USER_ID), any(), any(DateRange.class)))
        .thenReturn(Flux.empty());
    when(transactionSummaryRepository.totalsByTag(eq(USER_ID), any(), any(DateRange.class)))
        .thenReturn(Flux.empty());

    StepVerifier.create(transactionSummaryService.summarize(USER_ID, criteria()))
        .assertNext(summary -> {
          assertEquals(new BigDecimal("4865.00"), summary.getNet());
          assertEquals(4L, summary.getTransactionCount());
        })
        .verifyComplete();
  }
}
