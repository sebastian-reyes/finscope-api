package com.sreyes.finscope.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreyes.finscope.model.query.AmountTotal;
import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.model.query.TransactionSummaryCriteria;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Pruebas de {@link TransactionSummaryRepository} contra PostgreSQL.
 *
 * <p>Son las cifras del inicio y del análisis. Lo que se fija aquí es lo que solo puede
 * romperse en el SQL: que el reparto por categoría sume el total, que el desglose por tag
 * cuente entero cada movimiento en cada tag sin perder los que no llevan ninguno, y que la
 * conversión de monedas use el tipo de cambio de cada movimiento y no otro.</p>
 */
@Import(TransactionSummaryRepository.class)
class TransactionSummaryRepositoryTest extends PostgresRepositoryTest {

  private static final int MONTH = 8;
  private static final int YEAR = 2026;
  private static final LocalDateTime DATE = LocalDateTime.of(YEAR, MONTH, 10, 12, 0);

  @Autowired
  private TransactionSummaryRepository summaries;

  /** El reparto por categoría suma exactamente el total del periodo. */
  @Test
  void categoryBreakdownAddsUpToTheTotal() {
    long userId = user();
    long food = category(userId, "Comida");
    long home = category(userId, "Hogar");
    transaction(userId, food, EXPENSE, "100.00", DATE);
    transaction(userId, food, EXPENSE, "50.25", DATE);
    transaction(userId, home, EXPENSE, "300.00", DATE);
    transaction(userId, home, INCOME, "1000.00", DATE);
    transaction(userId, home, EXPENSE, "999.00", LocalDateTime.of(YEAR, MONTH + 1, 1, 0, 0));

    TransactionSummaryCriteria criteria = criteria(null, null);
    Map<String, BigDecimal> byType = byKey(summaries.totalsByType(userId, criteria, range())
        .collectList().block(), AmountTotal::typeCode);
    List<AmountTotal> byCategory = summaries.totalsByCategory(userId, criteria, range())
        .collectList().block();

    BigDecimal expenseByCategory = byCategory.stream()
        .filter(total -> "EXPENSE".equals(total.typeCode()))
        .map(AmountTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(byType.get("EXPENSE")).isEqualByComparingTo("450.25");
    assertThat(byType.get("INCOME")).isEqualByComparingTo("1000.00");
    assertThat(expenseByCategory).isEqualByComparingTo(byType.get("EXPENSE"));
    assertThat(byCategory).allSatisfy(total -> {
      assertThat(total.categoryId()).isNotNull();
      assertThat(total.categoryName()).isNotNull();
    });
  }

  /**
   * Un movimiento con dos tags cuenta entero en cada uno, y los que no llevan ninguno se
   * agrupan bajo un nombre nulo en lugar de desaparecer.
   */
  @Test
  void tagBreakdownCountsEachTagAndKeepsUntagged() {
    long userId = user();
    long food = category(userId, "Comida");
    long work = tag(userId, "Trabajo");
    long trip = tag(userId, "Viaje");
    long both = transaction(userId, food, EXPENSE, "80.00", DATE);
    tagTransaction(both, work);
    tagTransaction(both, trip);
    transaction(userId, food, EXPENSE, "20.00", DATE);

    List<AmountTotal> byTag = summaries.totalsByTag(userId, criteria(null, null), range())
        .collectList().block();

    assertThat(byTag).hasSize(3);
    assertThat(total(byTag, "Trabajo")).isEqualByComparingTo("80.00");
    assertThat(total(byTag, "Viaje")).isEqualByComparingTo("80.00");
    assertThat(total(byTag, null)).isEqualByComparingTo("20.00");
  }

  /**
   * A soles, cada movimiento en dólares se convierte con el tipo de cambio que se guardó al
   * registrarlo, no con uno común: dos compras de 10 dólares hechas a 3.70 y a 3.80 son 75
   * soles.
   */
  @Test
  void conversionToBaseUsesEachMovementsOwnRate() {
    long userId = user();
    long food = category(userId, "Comida");
    transaction(userId, food, EXPENSE, "10.00", "USD", "3.700000", DATE, null);
    transaction(userId, food, EXPENSE, "10.00", "USD", "3.800000", DATE, null);
    transaction(userId, food, EXPENSE, "25.00", DATE);

    BigDecimal expense = expense(userId, criteria("PEN", BigDecimal.ONE));

    assertThat(expense).isEqualByComparingTo("100.00");
  }

  /**
   * A dólares, lo que ya está en dólares entra tal cual y lo demás pasa por la base y se
   * divide por el tipo de referencia que indica el cliente.
   */
  @Test
  void conversionToAnotherCurrencyDividesByTheReferenceRate() {
    long userId = user();
    long food = category(userId, "Comida");
    transaction(userId, food, EXPENSE, "10.00", "USD", "3.700000", DATE, null);
    transaction(userId, food, EXPENSE, "35.50", DATE);

    BigDecimal expense = expense(userId, criteria("USD", new BigDecimal("3.55")));

    assertThat(expense).isEqualByComparingTo("20.00");
  }

  /** Sin conversión, cada moneda va aparte y ninguna se suma con otra. */
  @Test
  void currencyBreakdownNeverMixesCurrencies() {
    long userId = user();
    long food = category(userId, "Comida");
    transaction(userId, food, EXPENSE, "10.00", "USD", "3.700000", DATE, null);
    transaction(userId, food, EXPENSE, "35.50", DATE);

    Map<String, BigDecimal> byCurrency = byKey(summaries
        .totalsByCurrency(userId, criteria(null, null), range()).collectList().block(),
        AmountTotal::currency);

    assertThat(byCurrency).containsOnlyKeys("USD", "PEN");
    assertThat(byCurrency.get("USD")).isEqualByComparingTo("10.00");
    assertThat(byCurrency.get("PEN")).isEqualByComparingTo("35.50");
  }

  private BigDecimal expense(long userId, TransactionSummaryCriteria criteria) {
    return byKey(summaries.totalsByType(userId, criteria, range()).collectList().block(),
        AmountTotal::typeCode).get("EXPENSE");
  }

  private static TransactionSummaryCriteria criteria(String convertTo, BigDecimal rate) {
    return new TransactionSummaryCriteria(MONTH, YEAR, null, null, null, null, null, null, null,
        convertTo, rate);
  }

  private static DateRange range() {
    return period(MONTH, YEAR);
  }

  private static BigDecimal total(List<AmountTotal> totals, String tagName) {
    return totals.stream()
        .filter(total -> Objects.equals(total.tagName(), tagName))
        .findFirst().orElseThrow().total();
  }

  private static Map<String, BigDecimal> byKey(List<AmountTotal> totals,
                                               Function<AmountTotal, String> key) {
    return totals.stream().collect(Collectors.toMap(key, AmountTotal::total));
  }
}
