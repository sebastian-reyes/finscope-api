package com.sreyes.finscope.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreyes.finscope.model.entity.Loan;
import com.sreyes.finscope.model.entity.LoanInstallment;
import com.sreyes.finscope.model.entity.LoanPayment;
import com.sreyes.finscope.model.query.DueLoanNotice;
import com.sreyes.finscope.model.query.NotificationSettings;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Pruebas de los repositorios de préstamos y de la consulta de avisos de cuotas contra
 * PostgreSQL.
 *
 * <p>La consulta de avisos repite en SQL la regla con la que la pantalla reparte lo devuelto
 * entre las cuotas. Si las dos se separaran, el teléfono avisaría de una cuota que la
 * pantalla da por cubierta, o callaría una que sigue pendiente. Ninguna otra prueba la
 * ejecuta: las del servicio de avisos reciben las cuotas ya resueltas.</p>
 */
@Import(NotificationRepository.class)
class LoanRepositoryTest extends PostgresRepositoryTest {

  private static final LocalDate LENT_ON = LocalDate.of(2026, 9, 1);

  @Autowired
  private LoanRepository loans;

  @Autowired
  private LoanInstallmentRepository installments;

  @Autowired
  private LoanPaymentRepository payments;

  @Autowired
  private NotificationRepository notifications;

  private Loan loan(long userId, String name) {
    return loans.save(new Loan(null, userId, name, new BigDecimal("900.00"), BigDecimal.ZERO,
        "PEN", LENT_ON, null, null)).block();
  }

  /** Tres cuotas de 300: 1 de octubre, 1 de noviembre y 1 de diciembre. */
  private void plan(Loan loan) {
    installments.saveAll(List.of(
        new LoanInstallment(null, loan.getId(), 1, LocalDate.of(2026, 10, 1),
            new BigDecimal("300.00")),
        new LoanInstallment(null, loan.getId(), 2, LocalDate.of(2026, 11, 1),
            new BigDecimal("300.00")),
        new LoanInstallment(null, loan.getId(), 3, LocalDate.of(2026, 12, 1),
            new BigDecimal("300.00")))).blockLast();
  }

  private void pay(Loan loan, String amount) {
    payments.save(new LoanPayment(null, loan.getId(), LocalDate.of(2026, 10, 1),
        new BigDecimal(amount), null)).block();
  }

  private void subscribe(long userId) {
    db.sql("""
            INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth)
            VALUES (:userId, :endpoint, 'p256dh', 'auth')
            """)
        .bind("userId", userId)
        .bind("endpoint", "https://push.test/" + UUID.randomUUID())
        .fetch().rowsUpdated().block();
  }

  private List<DueLoanNotice> due(long userId, LocalDate from, LocalDate to) {
    return notifications.findLoanInstallmentsDue(from, to)
        .filter(notice -> notice.userId() == userId)
        .collectList().block();
  }

  @Test
  void entitiesRoundTripWithTheirDates() {
    long userId = user();
    Loan saved = loan(userId, "Juan");
    plan(saved);
    pay(saved, "120.50");

    Loan read = loans.findByIdAndUserId(saved.getId(), userId).block();
    assertThat(read).isNotNull();
    assertThat(read.getLentOn()).isEqualTo(LENT_ON);
    assertThat(read.getInterest()).isEqualByComparingTo("0");
    assertThat(installments.findByLoanIdOrderByNumber(saved.getId()).collectList().block())
        .extracting(LoanInstallment::getNumber).containsExactly(1, 2, 3);
    assertThat(payments.findByLoanIdOrderByPaidOnDescIdDesc(saved.getId()).collectList().block())
        .singleElement()
        .satisfies(p -> assertThat(p.getAmount()).isEqualByComparingTo("120.50"));
  }

  @Test
  void listingQueriesStayWithinTheUser() {
    long userId = user();
    long otherId = user();
    plan(loan(userId, "Juan"));
    Loan foreign = loan(otherId, "Ana");
    plan(foreign);
    pay(foreign, "50.00");

    assertThat(loans.findByUserId(userId).collectList().block()).hasSize(1);
    assertThat(installments.findByUserId(userId).collectList().block()).hasSize(3);
    assertThat(payments.findByUserId(userId).collectList().block()).isEmpty();
    assertThat(loans.findByIdAndUserId(foreign.getId(), userId).block()).isNull();
  }

  @Test
  void deletingTheLoanTakesPlanAndPaymentsWithIt() {
    long userId = user();
    Loan loan = loan(userId, "Juan");
    plan(loan);
    pay(loan, "100.00");

    loans.delete(loan).block();

    assertThat(installments.findByUserId(userId).collectList().block()).isEmpty();
    assertThat(payments.findByUserId(userId).collectList().block()).isEmpty();
  }

  /**
   * Con 450 devueltos, la primera cuota está cubierta y de la segunda faltan 150. El aviso
   * de la primera no sale y el de la segunda dice lo que falta, no su importe entero.
   */
  @Test
  void dueInstallmentsFollowTheSameOrderAsTheScreen() {
    long userId = user();
    subscribe(userId);
    Loan loan = loan(userId, "Juan");
    plan(loan);
    pay(loan, "450.00");

    assertThat(due(userId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1))).isEmpty();

    List<DueLoanNotice> november = due(userId, LocalDate.of(2026, 10, 29),
        LocalDate.of(2026, 11, 1));
    assertThat(november).singleElement().satisfies(notice -> {
      assertThat(notice).hasNoNullFieldsOrProperties();
      assertThat(notice.loanId()).isEqualTo(loan.getId());
      assertThat(notice.borrowerName()).isEqualTo("Juan");
      assertThat(notice.number()).isEqualTo(2);
      assertThat(notice.installmentCount()).isEqualTo(3);
      assertThat(notice.dueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
      assertThat(notice.pending()).isEqualByComparingTo("150.00");
    });
  }

  @Test
  void closedLoansAndUsersWithoutDevicesOrWithTheNoticeOffAreNotNotified() {
    LocalDate day = LocalDate.of(2026, 10, 1);

    long closedOwner = user();
    subscribe(closedOwner);
    Loan closed = loan(closedOwner, "Juan");
    closed.setClosedOn(LENT_ON.plusDays(5));
    loans.save(closed).block();
    plan(closed);

    long withoutDevice = user();
    plan(loan(withoutDevice, "Ana"));

    long optedOut = user();
    subscribe(optedOut);
    plan(loan(optedOut, "Luis"));
    NotificationSettings settings = notifications.saveSettings(optedOut, null, null, false)
        .block();

    assertThat(settings).isEqualTo(new NotificationSettings(true, true, false));
    assertThat(due(closedOwner, day, day)).isEmpty();
    assertThat(due(withoutDevice, day, day)).isEmpty();
    assertThat(due(optedOut, day, day)).isEmpty();
  }

  @Test
  void loanNoticesCanBeClaimedOncePerInstallment() {
    long userId = user();

    assertThat(notifications.claim(userId, "LOAN_DUE_SOON", 40L, "2026-10-01").block())
        .isTrue();
    assertThat(notifications.claim(userId, "LOAN_DUE_SOON", 40L, "2026-10-01").block())
        .isFalse();
    assertThat(notifications.claim(userId, "LOAN_OVERDUE", 40L, "2026-10-01").block())
        .isTrue();
  }
}
