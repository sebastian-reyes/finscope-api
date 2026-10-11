package com.sreyes.finscope.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.model.query.RecurringDetail;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de {@link RecurringTransactionRepository} contra PostgreSQL.
 *
 * <p>El estado de cada mes de un fijo no se guarda: la consulta trae los hechos —si vence,
 * si se omitió, con qué movimiento se confirmó— y el servicio los combina. Si la consulta
 * trae mal un hecho, el servicio decide bien sobre datos falsos, y sus pruebas, que reciben
 * esos hechos ya hechos, no pueden verlo.</p>
 */
class RecurringTransactionRepositoryTest extends PostgresRepositoryTest {

  private static final int MONTH = 8;
  private static final int YEAR = 2026;

  @Autowired
  private RecurringTransactionRepository recurrings;

  /**
   * La proyección es la que más componentes tiene del proyecto, y por tanto la que más
   * fácil puede salir a nulo entera si uno de ellos coincide con una propiedad de la
   * entidad. Un fijo confirmado los rellena todos.
   */
  @Test
  void detailProjectionIsFullyPopulated() {
    long userId = user();
    long home = category(userId, "Hogar");
    long recurringId = recurring(userId, home, "99.00", 28, 1, 1, YEAR);
    long paymentId = transaction(userId, home, EXPENSE, "101.50", "PEN", null,
        LocalDateTime.of(YEAR, MONTH, 28, 0, 0), recurringId);

    RecurringDetail detail = detail(userId, recurringId, MONTH, YEAR);

    assertThat(detail).hasNoNullFieldsOrProperties();
    assertThat(detail.recurringId()).isEqualTo(recurringId);
    assertThat(detail.recurringCategoryId()).isEqualTo(home);
    assertThat(detail.recurringCategoryName()).isEqualTo("Hogar");
    assertThat(detail.recurringTypeId()).isEqualTo(EXPENSE);
    assertThat(detail.recurringTypeCode()).isEqualTo("EXPENSE");
    assertThat(detail.recurringDescription()).isEqualTo("Internet");
    assertThat(detail.recurringAmount()).isEqualByComparingTo("99.00");
    assertThat(detail.recurringCurrency()).isEqualTo("PEN");
    assertThat(detail.recurringDayOfMonth()).isEqualTo(28);
    assertThat(detail.recurringEveryMonths()).isEqualTo(1);
    assertThat(detail.recurringStartMonth()).isEqualTo(1);
    assertThat(detail.recurringStartYear()).isEqualTo(YEAR);
    assertThat(detail.recurringActive()).isTrue();
    assertThat(detail.recurringMonth()).isEqualTo(MONTH);
    assertThat(detail.recurringYear()).isEqualTo(YEAR);
    assertThat(detail.recurringDue()).isTrue();
    assertThat(detail.recurringSkipped()).isFalse();
    assertThat(detail.recurringTransactionId()).isEqualTo(paymentId);
    assertThat(detail.recurringPaidAmount()).isEqualByComparingTo("101.50");
    assertThat(detail.recurringPaidDate()).isEqualTo(LocalDateTime.of(YEAR, MONTH, 28, 0, 0));
  }

  /**
   * El fijo que vence el día 1 se confirma con un movimiento a las 00:00 del día 1. Ese
   * movimiento es del mes en que vence y no puede marcar como pagado el mes anterior: con el
   * fin de mes en nanosegundos, PostgreSQL lo redondeaba a esa misma medianoche y el rango
   * de agosto se quedaba con el pago de septiembre.
   */
  @Test
  void paymentAtMidnightOfTheFirstBelongsOnlyToItsOwnMonth() {
    long userId = user();
    long home = category(userId, "Hogar");
    long rent = recurring(userId, home, "1200.00", 1, 1, 1, YEAR);
    transaction(userId, home, EXPENSE, "1200.00", "PEN", null,
        LocalDateTime.of(YEAR, MONTH + 1, 1, 0, 0), rent);

    assertThat(detail(userId, rent, MONTH, YEAR).recurringTransactionId()).isNull();
    assertThat(detail(userId, rent, MONTH + 1, YEAR).recurringTransactionId()).isNotNull();
  }

  /** La regla de vencimiento: desde el mes de arranque y cada tantos meses como diga. */
  @Test
  void dueFollowsStartAndRhythm() {
    long userId = user();
    long home = category(userId, "Hogar");
    long monthly = recurring(userId, home, "10.00", 5, 1, MONTH, YEAR);
    long bimonthly = recurring(userId, home, "10.00", 5, 2, MONTH, YEAR);
    long yearly = recurring(userId, home, "10.00", 5, 12, MONTH, YEAR - 1);

    assertThat(detail(userId, monthly, MONTH - 1, YEAR).recurringDue()).isFalse();
    assertThat(detail(userId, monthly, MONTH, YEAR).recurringDue()).isTrue();
    assertThat(detail(userId, bimonthly, MONTH + 1, YEAR).recurringDue()).isFalse();
    assertThat(detail(userId, bimonthly, MONTH + 2, YEAR).recurringDue()).isTrue();
    assertThat(detail(userId, yearly, MONTH, YEAR).recurringDue()).isTrue();
    assertThat(detail(userId, yearly, MONTH + 1, YEAR).recurringDue()).isFalse();
  }

  /** Un fijo pausado no vence nunca, aunque le toque. */
  @Test
  void pausedRecurringIsNeverDue() {
    long userId = user();
    long recurringId = recurring(userId, category(userId, "Hogar"), "10.00", 5, 1, 1, YEAR);
    db.sql("UPDATE recurring_transactions SET active = false WHERE id_recurring = :id")
        .bind("id", recurringId).then().block();

    assertThat(detail(userId, recurringId, MONTH, YEAR).recurringDue()).isFalse();
  }

  /** La omisión es de un mes concreto y no se arrastra a los demás. */
  @Test
  void skipAppliesOnlyToItsMonth() {
    long userId = user();
    long recurringId = recurring(userId, category(userId, "Hogar"), "10.00", 5, 1, 1, YEAR);
    skip(recurringId, MONTH, YEAR);

    assertThat(detail(userId, recurringId, MONTH, YEAR).recurringSkipped()).isTrue();
    assertThat(detail(userId, recurringId, MONTH + 1, YEAR).recurringSkipped()).isFalse();
  }

  /** El listado acota por usuario y ordena por día del mes. */
  @Test
  void listsOnlyTheUsersRecurringByDay() {
    long userId = user();
    long home = category(userId, "Hogar");
    long late = recurring(userId, home, "10.00", 25, 1, 1, YEAR);
    long early = recurring(userId, home, "10.00", 3, 1, 1, YEAR);
    long stranger = user();
    recurring(stranger, category(stranger, "Hogar"), "10.00", 1, 1, 1, YEAR);

    DateRange range = period(MONTH, YEAR);
    List<RecurringDetail> details = recurrings
        .findDetailsByPeriod(userId, MONTH, YEAR, range.from(), range.to())
        .collectList().block();

    assertThat(details).extracting(RecurringDetail::recurringId).containsExactly(early, late);
  }

  /** Al borrar una categoría sus fijos pasan a otra, y solo los de ese usuario. */
  @Test
  void reassignCategoryMovesOnlyTheUsersRecurring() {
    long userId = user();
    long source = category(userId, "Hogar");
    long target = category(userId, "Otros");
    long recurringId = recurring(userId, source, "10.00", 5, 1, 1, YEAR);

    Long moved = recurrings.reassignCategory(userId, source, target).block();

    assertThat(moved).isEqualTo(1L);
    assertThat(recurrings.findByIdAndUserId(recurringId, userId).block().getCategoryId())
        .isEqualTo(target);
  }

  private RecurringDetail detail(long userId, long recurringId, int month, int year) {
    DateRange range = period(month, year);
    return recurrings.findDetailById(userId, recurringId, month, year, range.from(), range.to())
        .block();
  }
}
