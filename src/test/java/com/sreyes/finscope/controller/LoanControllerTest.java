package com.sreyes.finscope.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.exception.custom.InvalidLoanPlanException;
import com.sreyes.finscope.exception.custom.LoanNotFoundException;
import com.sreyes.finscope.exception.custom.LoanOverpaymentException;
import com.sreyes.finscope.model.entity.Loan;
import com.sreyes.finscope.model.entity.LoanInstallment;
import com.sreyes.finscope.model.entity.LoanPayment;
import com.sreyes.finscope.model.query.LoanDetail;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.LoanService;
import com.sreyes.finscope.util.mapper.LoanMapperImpl;
import com.sreyes.finscope.util.rules.LoanRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
 * Pruebas del contrato HTTP expuesto por {@link LoanController}. El mapper real se importa
 * en lugar de simularse porque lo que se comprueba es la forma del JSON: las cifras
 * derivadas y el estado de cada cuota, que es lo que la pantalla pinta tal cual.
 */
@WebFluxTest(LoanController.class)
@Import({TimeConfig.class, LoanMapperImpl.class})
class LoanControllerTest {

  private static final Long USER_ID = 7L;
  private static final Long LOAN_ID = 21L;
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

  @Autowired
  private WebTestClient webTestClient;

  @MockitoBean
  private LoanService loanService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutateWith(mockUser()).mutateWith(csrf());
    when(authenticatedUser.currentUserId()).thenReturn(Mono.just(USER_ID));
  }

  /** Préstamo de 1000 + 200 en tres cuotas de 400, con 550 devueltos. */
  private static LoanDetail detail() {
    Loan loan = new Loan(LOAN_ID, USER_ID, "Juan", new BigDecimal("1000.00"),
        new BigDecimal("200.00"), "USD", LocalDate.of(2026, 9, 1), "Para el auto", null);
    List<LoanInstallment> plan = List.of(
        new LoanInstallment(1L, LOAN_ID, 1, LocalDate.of(2026, 10, 1), new BigDecimal("400.00")),
        new LoanInstallment(2L, LOAN_ID, 2, LocalDate.of(2026, 11, 1), new BigDecimal("400.00")),
        new LoanInstallment(3L, LOAN_ID, 3, LocalDate.of(2026, 12, 1), new BigDecimal("400.00")));
    List<LoanPayment> payments = List.of(
        new LoanPayment(9L, LOAN_ID, LocalDate.of(2026, 10, 1), new BigDecimal("550.00"),
            "Transferencia"));
    return LoanRules.statement(loan, plan, payments, TODAY);
  }

  @Test
  @DisplayName("Lista los préstamos con sus cifras, sus cuotas y sus pagos")
  void listsLoans() {
    when(loanService.findLoans(USER_ID)).thenReturn(Flux.just(detail()));

    webTestClient.get().uri("/loans")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].id").isEqualTo(21)
        .jsonPath("$[0].borrowerName").isEqualTo("Juan")
        .jsonPath("$[0].currency").isEqualTo("USD")
        .jsonPath("$[0].lentOn").isEqualTo("2026-09-01")
        .jsonPath("$[0].total").isEqualTo(1200.00)
        .jsonPath("$[0].paid").isEqualTo(550.00)
        .jsonPath("$[0].outstanding").isEqualTo(650.00)
        .jsonPath("$[0].overdueAmount").isEqualTo(250.00)
        .jsonPath("$[0].status").isEqualTo("OVERDUE")
        .jsonPath("$[0].nextDueDate").isEqualTo("2026-11-01")
        .jsonPath("$[0].nextDueAmount").isEqualTo(250.00)
        .jsonPath("$[0].installments[1].status").isEqualTo("OVERDUE")
        .jsonPath("$[0].installments[1].paid").isEqualTo(150.00)
        .jsonPath("$[0].installments[2].status").isEqualTo("PENDING")
        .jsonPath("$[0].payments[0].note").isEqualTo("Transferencia")
        .jsonPath("$[0].payments[0].paidOn").isEqualTo("2026-10-01");
  }

  @Test
  @DisplayName("Registra un préstamo y responde 201")
  void createsLoan() {
    when(loanService.createLoan(eq(USER_ID), any())).thenReturn(Mono.just(detail()));

    webTestClient.post().uri("/loans")
        .bodyValue(Map.of(
            "borrowerName", "Juan",
            "principal", 1000,
            "interest", 200,
            "currency", "USD",
            "lentOn", "2026-09-01",
            "installments", List.of(Map.of("dueDate", "2026-10-01", "amount", 1200))))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo(21);
  }

  @Test
  @DisplayName("Un préstamo sin cuotas no llega al servicio")
  void rejectsLoanWithoutPlan() {
    webTestClient.post().uri("/loans")
        .bodyValue(Map.of(
            "borrowerName", "Juan",
            "principal", 1000,
            "lentOn", "2026-09-01",
            "installments", List.of()))
        .exchange()
        .expectStatus().isBadRequest();

    verify(loanService, never()).createLoan(any(), any());
  }

  @Test
  @DisplayName("Un plan que no cuadra responde 400 con su código")
  void reportsInvalidPlan() {
    when(loanService.createLoan(eq(USER_ID), any()))
        .thenReturn(Mono.error(new InvalidLoanPlanException("does not add up")));

    webTestClient.post().uri("/loans")
        .bodyValue(Map.of(
            "borrowerName", "Juan",
            "principal", 1000,
            "lentOn", "2026-09-01",
            "installments", List.of(Map.of("dueDate", "2026-10-01", "amount", 900))))
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.code").isEqualTo("INVALID_LOAN_PLAN");
  }

  @Test
  @DisplayName("Un pago mayor que lo que falta responde 409")
  void reportsOverpayment() {
    when(loanService.addPayment(eq(USER_ID), eq(LOAN_ID), any()))
        .thenReturn(Mono.error(new LoanOverpaymentException("too much")));

    webTestClient.post().uri("/loans/21/payments")
        .bodyValue(Map.of("paidOn", "2026-10-10", "amount", 5000))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.code").isEqualTo("LOAN_OVERPAYMENT");
  }

  @Test
  @DisplayName("Registrar un pago responde 201 con el préstamo actualizado")
  void createsPayment() {
    when(loanService.addPayment(eq(USER_ID), eq(LOAN_ID), any()))
        .thenReturn(Mono.just(detail()));

    webTestClient.post().uri("/loans/21/payments")
        .bodyValue(Map.of("paidOn", "2026-10-10", "amount", 100))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.outstanding").isEqualTo(650.00);
  }

  @Test
  @DisplayName("Borrar un pago responde con el préstamo tal y como queda")
  void deletesPayment() {
    when(loanService.deletePayment(USER_ID, LOAN_ID, 9L)).thenReturn(Mono.just(detail()));

    webTestClient.delete().uri("/loans/21/payments/9")
        .exchange()
        .expectStatus().isOk();

    verify(loanService).deletePayment(USER_ID, LOAN_ID, 9L);
  }

  @Test
  @DisplayName("Un préstamo ajeno o inexistente responde 404")
  void reportsNotFound() {
    when(loanService.findLoan(USER_ID, LOAN_ID))
        .thenReturn(Mono.error(new LoanNotFoundException("Loan not found with id: 21")));

    webTestClient.get().uri("/loans/21")
        .exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.code").isEqualTo("LOAN_NOT_FOUND");
  }

  @Test
  @DisplayName("Eliminar responde 204")
  void deletesLoan() {
    when(loanService.deleteLoan(USER_ID, LOAN_ID)).thenReturn(Mono.empty());

    webTestClient.delete().uri("/loans/21")
        .exchange()
        .expectStatus().isNoContent();
  }
}
