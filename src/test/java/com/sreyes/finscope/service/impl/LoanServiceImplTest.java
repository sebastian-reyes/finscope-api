package com.sreyes.finscope.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.api.model.Currency;
import com.sreyes.finscope.api.model.LoanInstallmentRequest;
import com.sreyes.finscope.api.model.LoanStatus;
import com.sreyes.finscope.api.model.SaveLoanPaymentRequest;
import com.sreyes.finscope.api.model.SaveLoanRequest;
import com.sreyes.finscope.api.model.UpdateLoanRequest;
import com.sreyes.finscope.exception.custom.InvalidLoanPlanException;
import com.sreyes.finscope.exception.custom.LoanNotFoundException;
import com.sreyes.finscope.exception.custom.LoanOverpaymentException;
import com.sreyes.finscope.exception.custom.LoanPaymentNotFoundException;
import com.sreyes.finscope.model.entity.Loan;
import com.sreyes.finscope.model.entity.LoanInstallment;
import com.sreyes.finscope.model.entity.LoanPayment;
import com.sreyes.finscope.repository.LoanInstallmentRepository;
import com.sreyes.finscope.repository.LoanPaymentRepository;
import com.sreyes.finscope.repository.LoanRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link LoanServiceImpl}.
 *
 * Se centran en lo que dejaría un préstamo imposible de saldar o con cifras que no cuadran:
 * guardar el préstamo sin su plan, aceptar un plan que no suma el total, cambiar el importe
 * sin rehacer las cuotas, o registrar un pago mayor que lo que falta.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoanServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final Long LOAN_ID = 21L;
  private static final LocalDate LENT_ON = LocalDate.of(2026, 9, 1);

  /** 15 de octubre de 2026 a mediodía en Lima. */
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-15T17:00:00Z"), ZoneId.of("America/Lima"));

  @Mock
  private LoanRepository loanRepository;

  @Mock
  private LoanInstallmentRepository installmentRepository;

  @Mock
  private LoanPaymentRepository paymentRepository;

  private LoanServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new LoanServiceImpl(loanRepository, installmentRepository, paymentRepository,
        new ContextTransactionalOperator(), CLOCK);
    when(installmentRepository.findByLoanIdOrderByNumber(anyLong())).thenReturn(Flux.empty());
    when(paymentRepository.findByLoanIdOrderByPaidOnDescIdDesc(anyLong()))
        .thenReturn(Flux.empty());
  }

  private static Loan loan() {
    return new Loan(LOAN_ID, USER_ID, "Juan", new BigDecimal("1000.00"),
        new BigDecimal("100.00"), "PEN", LENT_ON, null, null);
  }

  private static LoanInstallment installment(int number, LocalDate dueDate, String amount) {
    return new LoanInstallment((long) number, LOAN_ID, number, dueDate, new BigDecimal(amount));
  }

  private static LoanInstallmentRequest item(LocalDate dueDate, String amount) {
    return new LoanInstallmentRequest(dueDate, new BigDecimal(amount));
  }

  private void existing(Loan loan, LoanPayment... payments) {
    when(loanRepository.findByIdAndUserId(LOAN_ID, USER_ID)).thenReturn(Mono.just(loan));
    when(loanRepository.save(any(Loan.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(paymentRepository.findByLoanIdOrderByPaidOnDescIdDesc(LOAN_ID))
        .thenReturn(Flux.just(payments));
  }

  private static LoanPayment paid(String amount) {
    return new LoanPayment(1L, LOAN_ID, LENT_ON.plusDays(10), new BigDecimal(amount), null);
  }

  @Test
  @DisplayName("Guarda el préstamo y su plan dentro de la misma transacción")
  @SuppressWarnings("unchecked")
  void createsLoanWithPlanAtomically() {
    AtomicBoolean loanInTransaction = new AtomicBoolean();
    AtomicBoolean planInTransaction = new AtomicBoolean();
    when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> Mono.deferContextual(
        context -> {
          loanInTransaction.set(ContextTransactionalOperator.inTransaction(context));
          Loan loan = invocation.getArgument(0);
          loan.setId(LOAN_ID);
          return Mono.just(loan);
        }));
    when(installmentRepository.saveAll(any(Iterable.class))).thenAnswer(invocation ->
        Flux.deferContextual(context -> {
          planInTransaction.set(ContextTransactionalOperator.inTransaction(context));
          return Flux.fromIterable(invocation.<Iterable<LoanInstallment>>getArgument(0));
        }));

    SaveLoanRequest request = new SaveLoanRequest("  Juan ", new BigDecimal("1000"), LENT_ON,
        List.of(item(LocalDate.of(2026, 10, 1), "550"), item(LocalDate.of(2026, 11, 1), "550")));
    request.setInterest(new BigDecimal("100"));
    request.setNotes("   ");

    StepVerifier.create(service.createLoan(USER_ID, request))
        .assertNext(detail -> {
          assertThat(detail.loan().getBorrowerName()).isEqualTo("Juan");
          assertThat(detail.loan().getCurrency()).isEqualTo("PEN");
          assertThat(detail.loan().getNotes()).isNull();
          assertThat(detail.total()).isEqualByComparingTo("1100.00");
        })
        .verifyComplete();

    assertThat(loanInTransaction).isTrue();
    assertThat(planInTransaction).isTrue();
    ArgumentCaptor<Iterable<LoanInstallment>> plan = ArgumentCaptor.forClass(Iterable.class);
    verify(installmentRepository).saveAll(plan.capture());
    assertThat(plan.getValue()).allSatisfy(i -> assertThat(i.getLoanId()).isEqualTo(LOAN_ID));
  }

  @Test
  @DisplayName("Un préstamo sin interés no necesita mandar el campo")
  void interestDefaultsToZero() {
    when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> {
      Loan loan = invocation.getArgument(0);
      loan.setId(LOAN_ID);
      return Mono.just(loan);
    });
    when(installmentRepository.saveAll(any(Iterable.class))).thenReturn(Flux.empty());

    SaveLoanRequest request = new SaveLoanRequest("Ana", new BigDecimal("300"), LENT_ON,
        List.of(item(LocalDate.of(2026, 10, 1), "300")));
    request.setInterest(null);
    request.setCurrency(Currency.USD);

    StepVerifier.create(service.createLoan(USER_ID, request))
        .assertNext(detail -> {
          assertThat(detail.loan().getInterest()).isEqualByComparingTo("0");
          assertThat(detail.loan().getCurrency()).isEqualTo("USD");
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("No guarda nada si el plan no suma lo que hay que devolver")
  void rejectsPlanThatDoesNotAddUp() {
    SaveLoanRequest request = new SaveLoanRequest("Juan", new BigDecimal("1000"), LENT_ON,
        List.of(item(LocalDate.of(2026, 10, 1), "900")));

    StepVerifier.create(service.createLoan(USER_ID, request))
        .expectError(InvalidLoanPlanException.class)
        .verify();

    verify(loanRepository, never()).save(any());
  }

  @Test
  @DisplayName("Cambiar el importe sin mandar el plan nuevo se rechaza")
  void amountChangeRequiresPlan() {
    existing(loan());
    UpdateLoanRequest request = new UpdateLoanRequest();
    request.setInterest(new BigDecimal("200"));

    StepVerifier.create(service.updateLoan(USER_ID, LOAN_ID, request))
        .expectError(InvalidLoanPlanException.class)
        .verify();

    verify(loanRepository, never()).save(any());
  }

  @Test
  @DisplayName("El total no puede bajar de lo ya devuelto")
  void totalCannotDropBelowPaid() {
    existing(loan(), paid("800.00"));
    UpdateLoanRequest request = new UpdateLoanRequest();
    request.setPrincipal(new BigDecimal("500"));
    request.setInterest(BigDecimal.ZERO);
    request.setInstallments(List.of(item(LocalDate.of(2026, 10, 1), "500")));

    StepVerifier.create(service.updateLoan(USER_ID, LOAN_ID, request))
        .expectError(InvalidLoanPlanException.class)
        .verify();
  }

  @Test
  @DisplayName("Con plan nuevo reemplaza las cuotas enteras")
  @SuppressWarnings("unchecked")
  void replacesThePlan() {
    existing(loan());
    when(installmentRepository.deleteByLoanId(LOAN_ID)).thenReturn(Mono.empty());
    when(installmentRepository.saveAll(any(Iterable.class))).thenReturn(Flux.empty());
    UpdateLoanRequest request = new UpdateLoanRequest();
    request.setInstallments(List.of(item(LocalDate.of(2026, 12, 1), "1100")));

    StepVerifier.create(service.updateLoan(USER_ID, LOAN_ID, request))
        .expectNextCount(1)
        .verifyComplete();

    verify(installmentRepository).deleteByLoanId(LOAN_ID);
    verify(installmentRepository).saveAll(any(Iterable.class));
  }

  @Test
  @DisplayName("Cerrar un préstamo lo fecha hoy en la zona de los usuarios; reabrirlo lo limpia")
  void closesAndReopens() {
    Loan loan = loan();
    existing(loan);
    UpdateLoanRequest close = new UpdateLoanRequest();
    close.setClosed(true);

    StepVerifier.create(service.updateLoan(USER_ID, LOAN_ID, close))
        .assertNext(detail -> {
          assertThat(detail.loan().getClosedOn()).isEqualTo(LocalDate.of(2026, 10, 15));
          assertThat(detail.status()).isEqualTo(LoanStatus.CLOSED);
        })
        .verifyComplete();

    UpdateLoanRequest reopen = new UpdateLoanRequest();
    reopen.setClosed(false);
    StepVerifier.create(service.updateLoan(USER_ID, LOAN_ID, reopen))
        .assertNext(detail -> assertThat(detail.loan().getClosedOn()).isNull())
        .verifyComplete();
  }

  @Test
  @DisplayName("Rechaza un pago mayor que lo que falta y dice cuánto falta")
  void rejectsOverpayment() {
    existing(loan(), paid("1000.00"));

    StepVerifier.create(service.addPayment(USER_ID, LOAN_ID,
            new SaveLoanPaymentRequest(LocalDate.of(2026, 10, 10), new BigDecimal("150"))))
        .expectErrorSatisfies(error -> assertThat(error)
            .isInstanceOf(LoanOverpaymentException.class)
            .hasMessageContaining("100.00"))
        .verify();

    verify(paymentRepository, never()).save(any());
  }

  @Test
  @DisplayName("Registra el pago y responde con el préstamo tal y como queda")
  void registersPayment() {
    existing(loan());
    when(installmentRepository.findByLoanIdOrderByNumber(LOAN_ID)).thenReturn(Flux.just(
        installment(1, LocalDate.of(2026, 10, 1), "1100.00")));
    when(paymentRepository.save(any(LoanPayment.class))).thenAnswer(invocation -> {
      LoanPayment payment = invocation.getArgument(0);
      when(paymentRepository.findByLoanIdOrderByPaidOnDescIdDesc(LOAN_ID))
          .thenReturn(Flux.just(payment));
      return Mono.just(payment);
    });

    SaveLoanPaymentRequest request =
        new SaveLoanPaymentRequest(LocalDate.of(2026, 10, 10), new BigDecimal("1100"));
    request.setNote(" en efectivo ");

    StepVerifier.create(service.addPayment(USER_ID, LOAN_ID, request))
        .assertNext(detail -> {
          assertThat(detail.status()).isEqualTo(LoanStatus.PAID);
          assertThat(detail.payments().getFirst().getNote()).isEqualTo("en efectivo");
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("Un pago de otro préstamo se comporta como inexistente")
  void paymentOfAnotherLoanIsNotFound() {
    existing(loan());
    when(paymentRepository.findByIdAndLoanId(99L, LOAN_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.deletePayment(USER_ID, LOAN_ID, 99L))
        .expectError(LoanPaymentNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Un préstamo ajeno se comporta como inexistente")
  void foreignLoanIsNotFound() {
    when(loanRepository.findByIdAndUserId(LOAN_ID, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(service.findLoan(USER_ID, LOAN_ID))
        .expectError(LoanNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("El listado reparte cuotas y pagos por préstamo y pone arriba lo vencido")
  void listsWithOverdueFirst() {
    Loan upToDate = loan();
    Loan late = new Loan(22L, USER_ID, "Ana", new BigDecimal("200.00"), BigDecimal.ZERO,
        "PEN", LENT_ON, null, null);
    when(loanRepository.findByUserId(USER_ID)).thenReturn(Flux.just(upToDate, late));
    when(installmentRepository.findByUserId(USER_ID)).thenReturn(Flux.just(
        installment(1, LocalDate.of(2026, 12, 1), "1100.00"),
        new LoanInstallment(5L, 22L, 1, LocalDate.of(2026, 10, 1), new BigDecimal("200.00"))));
    when(paymentRepository.findByUserId(USER_ID)).thenReturn(Flux.empty());

    StepVerifier.create(service.findLoans(USER_ID))
        .assertNext(detail -> {
          assertThat(detail.loan().getBorrowerName()).isEqualTo("Ana");
          assertThat(detail.status()).isEqualTo(LoanStatus.OVERDUE);
        })
        .assertNext(detail -> {
          assertThat(detail.loan().getBorrowerName()).isEqualTo("Juan");
          assertThat(detail.installments()).hasSize(1);
        })
        .verifyComplete();
  }
}
