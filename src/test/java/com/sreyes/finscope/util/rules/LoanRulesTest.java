package com.sreyes.finscope.util.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sreyes.finscope.api.model.LoanInstallmentRequest;
import com.sreyes.finscope.api.model.LoanInstallmentStatus;
import com.sreyes.finscope.api.model.LoanStatus;
import com.sreyes.finscope.exception.custom.InvalidLoanPlanException;
import com.sreyes.finscope.model.entity.Loan;
import com.sreyes.finscope.model.entity.LoanInstallment;
import com.sreyes.finscope.model.entity.LoanPayment;
import com.sreyes.finscope.model.query.LoanDetail;
import com.sreyes.finscope.model.query.LoanInstallmentDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link LoanRules}.
 *
 * Fijan lo que haría que la pantalla de préstamos mintiera: que un pago a medias se diera por
 * cubierto, que un pago grande no cubriera las cuotas siguientes, que una cuota se viera
 * vencida el mismo día en que todavía se puede pagar, o que se aceptara un plan que nunca
 * llega a saldarse.
 */
class LoanRulesTest {

  private static final LocalDate LENT_ON = LocalDate.of(2026, 9, 1);
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 1);

  private static Loan loan(String principal, String interest) {
    return new Loan(1L, 7L, "Juan", new BigDecimal(principal), new BigDecimal(interest), "PEN",
        LENT_ON, null, null);
  }

  /** Tres cuotas de 400: 1 de octubre, 1 de noviembre y 1 de diciembre. */
  private static List<LoanInstallment> threeInstallments() {
    return List.of(
        new LoanInstallment(10L, 1L, 1, LocalDate.of(2026, 10, 1), new BigDecimal("400.00")),
        new LoanInstallment(11L, 1L, 2, LocalDate.of(2026, 11, 1), new BigDecimal("400.00")),
        new LoanInstallment(12L, 1L, 3, LocalDate.of(2026, 12, 1), new BigDecimal("400.00")));
  }

  private static LoanPayment payment(String amount) {
    return new LoanPayment(null, 1L, LocalDate.of(2026, 10, 2), new BigDecimal(amount), null);
  }

  private static LoanInstallmentRequest item(LocalDate dueDate, String amount) {
    return new LoanInstallmentRequest(dueDate, new BigDecimal(amount));
  }

  @Test
  @DisplayName("Lo devuelto cubre las cuotas en orden y deja a medias la siguiente")
  void appliesPaymentsInOrder() {
    LoanDetail detail = LoanRules.statement(loan("1000.00", "200.00"), threeInstallments(),
        List.of(payment("300.00"), payment("250.00")), TODAY);

    List<LoanInstallmentDetail> installments = detail.installments();
    assertThat(installments.get(0).paid()).isEqualByComparingTo("400.00");
    assertThat(installments.get(0).status()).isEqualTo(LoanInstallmentStatus.PAID);
    assertThat(installments.get(1).paid()).isEqualByComparingTo("150.00");
    assertThat(installments.get(1).status()).isEqualTo(LoanInstallmentStatus.PARTIAL);
    assertThat(installments.get(2).paid()).isEqualByComparingTo("0");
    assertThat(installments.get(2).status()).isEqualTo(LoanInstallmentStatus.PENDING);

    assertThat(detail.total()).isEqualByComparingTo("1200.00");
    assertThat(detail.paid()).isEqualByComparingTo("550.00");
    assertThat(detail.outstanding()).isEqualByComparingTo("650.00");
    assertThat(detail.nextDueDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(detail.nextDueAmount()).isEqualByComparingTo("250.00");
  }

  @Test
  @DisplayName("El día que vence la cuota todavía no está vencida; al día siguiente sí")
  void dueDayIsNotOverdue() {
    LoanDetail onTheDay = LoanRules.statement(loan("1200.00", "0"), threeInstallments(),
        List.of(payment("400.00")), TODAY);
    assertThat(onTheDay.status()).isEqualTo(LoanStatus.ACTIVE);
    assertThat(onTheDay.overdueAmount()).isEqualByComparingTo("0");

    LoanDetail dayAfter = LoanRules.statement(loan("1200.00", "0"), threeInstallments(),
        List.of(payment("450.00")), TODAY.plusDays(1));
    assertThat(dayAfter.status()).isEqualTo(LoanStatus.OVERDUE);
    assertThat(dayAfter.overdueAmount()).isEqualByComparingTo("350.00");
    assertThat(dayAfter.installments().get(1).status())
        .isEqualTo(LoanInstallmentStatus.OVERDUE);
  }

  @Test
  @DisplayName("Devuelto todo, el préstamo queda saldado y sin próxima cuota")
  void fullyPaid() {
    LoanDetail detail = LoanRules.statement(loan("1200.00", "0"), threeInstallments(),
        List.of(payment("1200.00")), TODAY);

    assertThat(detail.status()).isEqualTo(LoanStatus.PAID);
    assertThat(detail.outstanding()).isEqualByComparingTo("0");
    assertThat(detail.nextDueDate()).isNull();
    assertThat(detail.installments())
        .allSatisfy(i -> assertThat(i.status()).isEqualTo(LoanInstallmentStatus.PAID));
  }

  @Test
  @DisplayName("Un préstamo cerrado no tiene nada vencido ni próxima cuota")
  void closedLoanExpectsNothing() {
    Loan loan = loan("1200.00", "0");
    loan.setClosedOn(TODAY);

    LoanDetail detail = LoanRules.statement(loan, threeInstallments(), List.of(),
        TODAY.plusMonths(3));

    assertThat(detail.status()).isEqualTo(LoanStatus.CLOSED);
    assertThat(detail.overdueAmount()).isEqualByComparingTo("0");
    assertThat(detail.nextDueDate()).isNull();
    assertThat(detail.outstanding()).isEqualByComparingTo("1200.00");
  }

  @Test
  @DisplayName("El plan se numera por fecha, no por el orden en que llega")
  void numbersThePlanByDate() {
    List<LoanInstallment> plan = LoanRules.plan(1L, List.of(
            item(LocalDate.of(2026, 12, 1), "600.00"),
            item(LocalDate.of(2026, 10, 1), "400.00")),
        new BigDecimal("1000.00"), LENT_ON);

    assertThat(plan).extracting(LoanInstallment::getNumber).containsExactly(1, 2);
    assertThat(plan).extracting(LoanInstallment::getDueDate)
        .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 1));
    assertThat(plan).allSatisfy(i -> assertThat(i.getLoanId()).isEqualTo(1L));
  }

  @Test
  @DisplayName("Rechaza un plan que no suma capital más interés")
  void rejectsPlanThatDoesNotAddUp() {
    List<LoanInstallmentRequest> plan = new ArrayList<>();
    plan.add(item(LocalDate.of(2026, 10, 1), "333.33"));
    plan.add(item(LocalDate.of(2026, 11, 1), "333.33"));
    plan.add(item(LocalDate.of(2026, 12, 1), "333.33"));

    assertThatThrownBy(() -> LoanRules.plan(1L, plan, new BigDecimal("1000.00"), LENT_ON))
        .isInstanceOf(InvalidLoanPlanException.class)
        .hasMessageContaining("999.99")
        .hasMessageContaining("1000.00");
  }

  @Test
  @DisplayName("Rechaza una cuota que vence antes de prestar el dinero")
  void rejectsInstallmentBeforeLending() {
    List<LoanInstallmentRequest> plan = List.of(item(LENT_ON.minusDays(1), "1000.00"));

    assertThatThrownBy(() -> LoanRules.plan(1L, plan, new BigDecimal("1000.00"), LENT_ON))
        .isInstanceOf(InvalidLoanPlanException.class);
  }

  @Test
  @DisplayName("Lista primero lo vencido, después lo abierto por fecha y al final lo saldado")
  void ordersTheListing() {
    LoanDetail paid = LoanRules.statement(loan("1200.00", "0"), threeInstallments(),
        List.of(payment("1200.00")), TODAY);
    LoanDetail overdue = LoanRules.statement(loan("1200.00", "0"), threeInstallments(),
        List.of(), TODAY);
    LoanDetail active = LoanRules.statement(loan("1200.00", "0"), threeInstallments(),
        List.of(payment("400.00")), TODAY);

    List<LoanDetail> sorted = new ArrayList<>(List.of(paid, active, overdue));
    sorted.sort(LoanRules.LISTING_ORDER);

    assertThat(sorted).containsExactly(overdue, active, paid);
  }
}
