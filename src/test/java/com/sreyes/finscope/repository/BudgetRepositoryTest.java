package com.sreyes.finscope.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreyes.finscope.model.entity.Budget;
import com.sreyes.finscope.model.query.BudgetProgress;
import com.sreyes.finscope.model.query.DateRange;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de {@link BudgetRepository} contra PostgreSQL.
 *
 * <p>El avance de un presupuesto no se guarda: lo calcula {@link BudgetRepository#PROGRESS_SELECT}
 * en cada lectura, con dos uniones externas y la regla de vencimiento de los fijos. Es la
 * cifra que el usuario mira para decidir si puede gastar, y ninguna otra prueba la ejecuta:
 * las del servicio reciben el avance ya hecho de un repositorio simulado.</p>
 */
class BudgetRepositoryTest extends PostgresRepositoryTest {

  private static final int MONTH = 8;
  private static final int YEAR = 2026;

  @Autowired
  private BudgetRepository budgets;

  /**
   * Lo gastado son los egresos de la categoría en el mes y en la moneda del plan, y nada
   * más. Cada movimiento de los que sobran rompe una regla distinta: si alguno se colara, la
   * cifra dejaría de coincidir con el desglose por categoría del resumen.
   */
  @Test
  void spentCountsOnlyExpensesOfTheCategoryMonthAndCurrency() {
    long userId = user();
    long food = category(userId, "Comida");
    long leisure = category(userId, "Ocio");
    long budgetId = budget(userId, food, MONTH, YEAR, "400.00");

    transaction(userId, food, EXPENSE, "100.00", LocalDateTime.of(YEAR, MONTH, 1, 0, 0));
    transaction(userId, food, EXPENSE, "20.50", LocalDateTime.of(YEAR, MONTH, 31, 23, 59, 59));
    transaction(userId, food, INCOME, "500.00", LocalDateTime.of(YEAR, MONTH, 10, 12, 0));
    transaction(userId, leisure, EXPENSE, "70.00", LocalDateTime.of(YEAR, MONTH, 10, 12, 0));
    transaction(userId, food, EXPENSE, "30.00", LocalDateTime.of(YEAR, MONTH + 1, 1, 0, 0));
    transaction(userId, food, EXPENSE, "15.00", LocalDateTime.of(YEAR, MONTH - 1, 31, 23, 59));
    transaction(userId, food, EXPENSE, "10.00", "USD", "3.700000",
        LocalDateTime.of(YEAR, MONTH, 5, 12, 0), null);

    BudgetProgress progress = byId(userId, budgetId);

    assertThat(progress.budgetSpent()).isEqualByComparingTo("120.50");
    assertThat(progress.budgetCommitted()).isEqualByComparingTo("0");
  }

  /**
   * La proyección sale entera del alias de cada columna. Si un componente del record se
   * llamara como una propiedad de {@link Budget}, el conversor buscaría la columna de la
   * entidad y lo devolvería todo a nulo sin ningún error, que es lo que este caso vigila.
   */
  @Test
  void progressProjectionIsFullyPopulated() {
    long userId = user();
    long food = category(userId, "Comida");
    long budgetId = budget(userId, food, MONTH, YEAR, "400.00");

    BudgetProgress progress = byId(userId, budgetId);

    assertThat(progress).hasNoNullFieldsOrProperties();
    assertThat(progress.budgetId()).isEqualTo(budgetId);
    assertThat(progress.budgetCategoryId()).isEqualTo(food);
    assertThat(progress.budgetCategoryName()).isEqualTo("Comida");
    assertThat(progress.budgetMonth()).isEqualTo(MONTH);
    assertThat(progress.budgetYear()).isEqualTo(YEAR);
    assertThat(progress.budgetCurrency()).isEqualTo("PEN");
    assertThat(progress.budgetAmount()).isEqualByComparingTo("400.00");
    assertThat(progress.budgetSpent()).isEqualByComparingTo("0");
  }

  /**
   * Lo comprometido son los fijos de gasto que vencen este mes y siguen pendientes. Un
   * fijo omitido, uno ya confirmado, uno pausado, uno bimestral en su mes libre y uno que
   * arranca después no comprometen nada.
   */
  @Test
  void committedCountsOnlyPendingRecurringDueThisMonth() {
    long userId = user();
    long home = category(userId, "Hogar");
    long budgetId = budget(userId, home, MONTH, YEAR, "500.00");

    recurring(userId, home, "99.00", 28, 1, 1, YEAR);
    recurring(userId, home, "40.00", 15, 12, MONTH, YEAR - 1);

    long skipped = recurring(userId, home, "11.00", 5, 1, 1, YEAR);
    skip(skipped, MONTH, YEAR);

    long confirmed = recurring(userId, home, "13.00", 5, 1, 1, YEAR);
    transaction(userId, home, EXPENSE, "13.00", "PEN", null,
        LocalDateTime.of(YEAR, MONTH, 5, 9, 0), confirmed);

    long paused = recurring(userId, home, "17.00", 5, 1, 1, YEAR);
    db.sql("UPDATE recurring_transactions SET active = false WHERE id_recurring = :id")
        .bind("id", paused).then().block();

    recurring(userId, home, "19.00", 5, 2, MONTH + 1, YEAR - 1);
    recurring(userId, home, "23.00", 5, 1, MONTH + 1, YEAR);

    BudgetProgress progress = byId(userId, budgetId);

    assertThat(progress.budgetCommitted()).isEqualByComparingTo("139.00");
    assertThat(progress.budgetSpent()).isEqualByComparingTo("13.00");
  }

  /** El listado del mes trae lo mismo que la lectura individual, en orden de categoría. */
  @Test
  void listsTheMonthInCategoryOrder() {
    long userId = user();
    long zeta = category(userId, "zeta");
    long alpha = category(userId, "Alfa");
    budget(userId, zeta, MONTH, YEAR, "10.00");
    budget(userId, alpha, MONTH, YEAR, "20.00");
    budget(userId, alpha, MONTH + 1, YEAR, "30.00");
    transaction(userId, alpha, EXPENSE, "5.00", LocalDateTime.of(YEAR, MONTH, 3, 10, 0));

    DateRange range = period(MONTH, YEAR);
    List<BudgetProgress> month = budgets
        .findProgressByPeriod(userId, MONTH, YEAR, range.from(), range.to())
        .collectList().block();

    assertThat(month).extracting(BudgetProgress::budgetCategoryName)
        .containsExactly("Alfa", "zeta");
    assertThat(month.get(0).budgetSpent()).isEqualByComparingTo("5.00");
  }

  /** Fijar dos veces la misma categoría, mes y moneda no pisa el primer importe. */
  @Test
  void insertIfAbsentKeepsTheExistingBudget() {
    long userId = user();
    long food = category(userId, "Comida");

    Long first = budgets.insertIfAbsent(userId, food, MONTH, YEAR, "PEN", new BigDecimal("100"))
        .block();
    Long second = budgets.insertIfAbsent(userId, food, MONTH, YEAR, "PEN", new BigDecimal("999"))
        .block();
    Long otherCurrency = budgets
        .insertIfAbsent(userId, food, MONTH, YEAR, "USD", new BigDecimal("50")).block();

    Budget kept = budgets.findByCategoryAndPeriod(userId, food, MONTH, YEAR, "PEN").block();
    assertThat(first).isEqualTo(1L);
    assertThat(second).isZero();
    assertThat(otherCurrency).isEqualTo(1L);
    assertThat(kept.getAmount()).isEqualByComparingTo("100");
  }

  /** Copiar un mes deja intacto lo que el destino ya tenía y repetirlo no cambia nada. */
  @Test
  void copyPeriodNeverOverwritesTheTarget() {
    long userId = user();
    long food = category(userId, "Comida");
    long home = category(userId, "Hogar");
    budget(userId, food, MONTH, YEAR, "400.00");
    budget(userId, home, MONTH, YEAR, "300.00");
    budget(userId, food, MONTH + 1, YEAR, "450.00");

    Long copied = budgets.copyPeriod(userId, MONTH, YEAR, MONTH + 1, YEAR).block();
    Long again = budgets.copyPeriod(userId, MONTH, YEAR, MONTH + 1, YEAR).block();

    assertThat(copied).isEqualTo(1L);
    assertThat(again).isZero();
    assertThat(budgets.findByCategoryAndPeriod(userId, food, MONTH + 1, YEAR, "PEN").block()
        .getAmount()).isEqualByComparingTo("450.00");
    assertThat(budgets.findByCategoryAndPeriod(userId, home, MONTH + 1, YEAR, "PEN").block()
        .getAmount()).isEqualByComparingTo("300.00");
  }

  /** Un presupuesto ajeno se comporta como uno que no existe. */
  @Test
  void anotherUsersBudgetIsInvisible() {
    long owner = user();
    long budgetId = budget(owner, category(owner, "Comida"), MONTH, YEAR, "400.00");
    long stranger = user();

    assertThat(budgets.findByIdAndUserId(budgetId, stranger).block()).isNull();
    assertThat(byId(stranger, budgetId)).isNull();
    assertThat(budgets.findByIdAndUserId(budgetId, owner).block())
        .hasNoNullFieldsOrProperties();
  }

  private BudgetProgress byId(long userId, long budgetId) {
    DateRange range = period(MONTH, YEAR);
    return budgets.findProgressById(userId, budgetId, MONTH, YEAR, range.from(), range.to())
        .block();
  }
}
