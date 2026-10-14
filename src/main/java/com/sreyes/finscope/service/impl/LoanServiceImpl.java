package com.sreyes.finscope.service.impl;

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
import com.sreyes.finscope.model.query.LoanDetail;
import com.sreyes.finscope.repository.LoanInstallmentRepository;
import com.sreyes.finscope.repository.LoanPaymentRepository;
import com.sreyes.finscope.repository.LoanRepository;
import com.sreyes.finscope.service.LoanService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.rules.CurrencyRules;
import com.sreyes.finscope.util.rules.LoanRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link LoanService}.
 *
 * <p>El préstamo y su plan se escriben siempre juntos y dentro de una transacción: un
 * préstamo guardado sin cuotas no sabría cuándo vence, y unas cuotas que no suman el total
 * dejarían el préstamo sin poder saldarse nunca.</p>
 *
 * <p>Las cifras se resuelven con {@link LoanRules} contra el día de hoy en la zona de los
 * usuarios. El reloj de la aplicación ya está en esa zona, así que «vencida» empieza a las
 * cero horas de Lima y no a las siete de la tarde, que es cuando cambia el día en UTC.</p>
 */
@Service
@RequiredArgsConstructor
public class LoanServiceImpl implements LoanService {

  private final LoanRepository loanRepository;
  private final LoanInstallmentRepository installmentRepository;
  private final LoanPaymentRepository paymentRepository;
  private final TransactionalOperator transactionalOperator;
  private final Clock clock;

  /**
   * {@inheritDoc}
   *
   * <p>Se leen las cuotas y los pagos de todos los préstamos con una consulta cada uno y se
   * reparten en memoria, en lugar de dos consultas por préstamo.</p>
   */
  @Override
  public Flux<LoanDetail> findLoans(Long userId) {
    return Mono.zip(
            loanRepository.findByUserId(userId).collectList(),
            installmentRepository.findByUserId(userId)
                .collect(Collectors.groupingBy(LoanInstallment::getLoanId)),
            paymentRepository.findByUserId(userId)
                .collect(Collectors.groupingBy(LoanPayment::getLoanId)))
        .flatMapMany(tuple -> {
          LocalDate today = LocalDate.now(clock);
          Map<Long, List<LoanInstallment>> installments = tuple.getT2();
          Map<Long, List<LoanPayment>> payments = tuple.getT3();
          return Flux.fromStream(tuple.getT1().stream()
              .map(loan -> LoanRules.statement(loan,
                  installments.getOrDefault(loan.getId(), List.of()),
                  payments.getOrDefault(loan.getId(), List.of()), today))
              .sorted(LoanRules.LISTING_ORDER));
        });
  }

  @Override
  public Mono<LoanDetail> findLoan(Long userId, Long id) {
    return requireLoan(userId, id).flatMap(this::detailOf);
  }

  @Override
  public Mono<LoanDetail> createLoan(Long userId, SaveLoanRequest request) {
    return Mono.defer(() -> {
      BigDecimal interest = request.getInterest() == null ? BigDecimal.ZERO
          : request.getInterest();
      Loan loan = new Loan(null, userId, request.getBorrowerName().strip(),
          LoanRules.money(request.getPrincipal()), LoanRules.money(interest),
          CurrencyRules.orBase(request.getCurrency()).getValue(), request.getLentOn(),
          blankToNull(request.getNotes()), null);
      List<LoanInstallment> plan = LoanRules.plan(null, request.getInstallments(),
          loan.getPrincipal().add(loan.getInterest()), loan.getLentOn());
      return transactionalOperator.transactional(loanRepository.save(loan)
          .flatMap(saved -> {
            plan.forEach(item -> item.setLoanId(saved.getId()));
            return installmentRepository.saveAll(plan).then(Mono.just(saved));
          }));
    }).flatMap(this::detailOf);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Si cambia el capital o el interés sin plan nuevo se rechaza en lugar de repartir la
   * diferencia: no hay una forma evidente de hacerlo, y elegir una a escondidas cambiaría
   * cuánto se espera cobrar en cada fecha sin que nadie lo haya decidido.</p>
   */
  @Override
  public Mono<LoanDetail> updateLoan(Long userId, Long id, UpdateLoanRequest request) {
    return requireLoan(userId, id)
        .flatMap(loan -> Mono.zip(paidOf(loan.getId()),
                installmentRepository.findByLoanIdOrderByNumber(loan.getId()).collectList())
            .flatMap(tuple -> {
              BigDecimal paid = tuple.getT1();
              List<LoanInstallment> current = tuple.getT2();
              boolean amountsChange = request.getPrincipal() != null
                  || request.getInterest() != null;
              if (amountsChange && request.getInstallments() == null) {
                return Mono.error(new InvalidLoanPlanException(Constants.LOAN_PLAN_REQUIRED));
              }
              apply(loan, request);
              BigDecimal total = loan.getPrincipal().add(loan.getInterest());
              if (total.compareTo(paid) < 0) {
                return Mono.error(new InvalidLoanPlanException(Constants.LOAN_TOTAL_BELOW_PAID
                    .replace("{}", paid.toPlainString())));
              }
              if (request.getInstallments() == null) {
                if (current.stream().anyMatch(i -> i.getDueDate().isBefore(loan.getLentOn()))) {
                  return Mono.error(new InvalidLoanPlanException(
                      Constants.LOAN_INSTALLMENT_BEFORE_LENT_ON));
                }
                return loanRepository.save(loan);
              }
              List<LoanInstallment> plan = LoanRules.plan(loan.getId(),
                  request.getInstallments(), total, loan.getLentOn());
              return transactionalOperator.transactional(loanRepository.save(loan)
                  .flatMap(saved -> installmentRepository.deleteByLoanId(saved.getId())
                      .thenMany(installmentRepository.saveAll(plan))
                      .then(Mono.just(saved))));
            }))
        .flatMap(this::detailOf);
  }

  @Override
  public Mono<Void> deleteLoan(Long userId, Long id) {
    return requireLoan(userId, id).flatMap(loanRepository::delete);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Un pago que supera lo que falta se rechaza: casi siempre es un importe mal escrito, y
   * aceptarlo dejaría un saldo negativo que no significa nada. Si de verdad devolvieron de
   * más, lo honesto es apuntar el interés que se ganó.</p>
   */
  @Override
  public Mono<LoanDetail> addPayment(Long userId, Long id, SaveLoanPaymentRequest request) {
    return requireLoan(userId, id)
        .flatMap(loan -> paidOf(loan.getId())
            .flatMap(paid -> {
              BigDecimal amount = LoanRules.money(request.getAmount());
              BigDecimal outstanding = loan.getPrincipal().add(loan.getInterest())
                  .subtract(paid);
              if (amount.compareTo(outstanding) > 0) {
                return Mono.error(new LoanOverpaymentException(Constants.LOAN_OVERPAYMENT
                    .replace("{}", outstanding.max(BigDecimal.ZERO).toPlainString())));
              }
              return paymentRepository.save(new LoanPayment(null, loan.getId(),
                  request.getPaidOn(), amount, blankToNull(request.getNote())));
            })
            .thenReturn(loan))
        .flatMap(this::detailOf);
  }

  @Override
  public Mono<LoanDetail> deletePayment(Long userId, Long id, Long paymentId) {
    return requireLoan(userId, id)
        .flatMap(loan -> paymentRepository.findByIdAndLoanId(paymentId, loan.getId())
            .switchIfEmpty(Mono.error(new LoanPaymentNotFoundException(
                Constants.LOAN_PAYMENT_NOT_FOUND + paymentId)))
            .flatMap(paymentRepository::delete)
            .thenReturn(loan))
        .flatMap(this::detailOf);
  }

  /**
   * Aplica al préstamo los campos presentes de la modificación.
   *
   * @param loan    préstamo a modificar
   * @param request cambios recibidos
   */
  private void apply(Loan loan, UpdateLoanRequest request) {
    if (request.getBorrowerName() != null) {
      loan.setBorrowerName(request.getBorrowerName().strip());
    }
    if (request.getPrincipal() != null) {
      loan.setPrincipal(LoanRules.money(request.getPrincipal()));
    }
    if (request.getInterest() != null) {
      loan.setInterest(LoanRules.money(request.getInterest()));
    }
    if (request.getCurrency() != null) {
      loan.setCurrency(request.getCurrency().getValue());
    }
    if (request.getLentOn() != null) {
      loan.setLentOn(request.getLentOn());
    }
    if (request.getNotes() != null) {
      loan.setNotes(blankToNull(request.getNotes()));
    }
    if (Boolean.TRUE.equals(request.getClosed()) && loan.getClosedOn() == null) {
      loan.setClosedOn(LocalDate.now(clock));
    } else if (Boolean.FALSE.equals(request.getClosed())) {
      loan.setClosedOn(null);
    }
  }

  /**
   * Resuelve un préstamo guardado contra el día de hoy.
   *
   * @param loan préstamo guardado
   * @return el préstamo con su plan, sus pagos y sus cifras
   */
  private Mono<LoanDetail> detailOf(Loan loan) {
    return Mono.zip(
            installmentRepository.findByLoanIdOrderByNumber(loan.getId()).collectList(),
            paymentRepository.findByLoanIdOrderByPaidOnDescIdDesc(loan.getId()).collectList())
        .map(tuple -> LoanRules.statement(loan, tuple.getT1(), tuple.getT2(),
            LocalDate.now(clock)));
  }

  /**
   * Suma lo devuelto de un préstamo.
   *
   * @param loanId identificador del préstamo
   * @return lo devuelto, cero si no hay pagos
   */
  private Mono<BigDecimal> paidOf(Long loanId) {
    return paymentRepository.findByLoanIdOrderByPaidOnDescIdDesc(loanId)
        .map(LoanPayment::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * Obtiene un préstamo del usuario o falla si no existe.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del préstamo
   * @return el préstamo encontrado
   */
  private Mono<Loan> requireLoan(Long userId, Long id) {
    return loanRepository.findByIdAndUserId(id, userId)
        .switchIfEmpty(Mono.error(new LoanNotFoundException(Constants.LOAN_NOT_FOUND + id)));
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
