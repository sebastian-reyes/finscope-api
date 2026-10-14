package com.sreyes.finscope.util.rules;

import com.sreyes.finscope.api.model.LoanInstallmentRequest;
import com.sreyes.finscope.api.model.LoanInstallmentStatus;
import com.sreyes.finscope.api.model.LoanStatus;
import com.sreyes.finscope.exception.custom.InvalidLoanPlanException;
import com.sreyes.finscope.model.entity.Loan;
import com.sreyes.finscope.model.entity.LoanInstallment;
import com.sreyes.finscope.model.entity.LoanPayment;
import com.sreyes.finscope.model.query.LoanDetail;
import com.sreyes.finscope.model.query.LoanInstallmentDetail;
import com.sreyes.finscope.util.constants.Constants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Reglas de los préstamos: cómo se valida un plan de cuotas y cómo se reparte lo devuelto
 * entre ellas.
 * Esta clase no debe ser instanciada.
 *
 * <p><strong>Lo devuelto se aplica en orden de cuota.</strong> Los pagos no se atan a una
 * cuota concreta: se suman y cubren primero la cuota 1, luego la 2, y así hasta que se
 * acaban. Es lo que hace cualquiera con un cuaderno, y es lo que permite registrar un pago a
 * medias o uno adelantado sin preguntar a qué cuota corresponde. La consulta de avisos de
 * {@code NotificationRepository} sigue la misma regla en SQL, para que el aviso nunca diga
 * que falta una cuota que la pantalla da por cubierta.</p>
 *
 * <p>Los importes se redondean a céntimos antes de comparar, igual que los guarda la base: una
 * cuota de 333.333 se guardaría como 333.33, y comparar sin redondear aceptaría un plan que
 * después no suma.</p>
 */
@UtilityClass
public final class LoanRules {

  /** Importe del que no se baja; también el de lo que falta en un préstamo saldado. */
  private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

  /**
   * Orden en que se listan los préstamos: primero los que tienen algo vencido, después los
   * abiertos por su próxima cuota, y al final los saldados y cerrados, del más reciente al
   * más antiguo. Lo que hay que reclamar va arriba.
   */
  public static final Comparator<LoanDetail> LISTING_ORDER =
      Comparator.comparingInt((LoanDetail detail) -> rank(detail.status()))
          .thenComparing(LoanDetail::nextDueDate,
              Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(detail -> detail.loan().getLentOn(), Comparator.reverseOrder())
          .thenComparing(detail -> detail.loan().getId(), Comparator.reverseOrder());

  /**
   * Redondea un importe a céntimos, que es como lo guarda la base.
   *
   * @param amount importe recibido
   * @return el importe con dos decimales
   */
  public static BigDecimal money(BigDecimal amount) {
    return amount.setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Comprueba un plan de cuotas y lo convierte en las cuotas a guardar.
   *
   * <p>Las cuotas se numeran por fecha y no por el orden en que llegan, porque el número es
   * lo que decide a qué cuota se aplica cada pago: si la de diciembre fuera la 1, un pago de
   * octubre cubriría diciembre. Dos cuotas del mismo día conservan el orden recibido.</p>
   *
   * @param loanId  préstamo al que pertenecen
   * @param plan    cuotas recibidas
   * @param total   lo que hay que devolver, capital más interés
   * @param lentOn  día en que se prestó
   * @return las cuotas numeradas, listas para guardarse
   * @throws InvalidLoanPlanException si no suman el total o alguna vence antes del préstamo
   */
  public static List<LoanInstallment> plan(Long loanId, List<LoanInstallmentRequest> plan,
                                           BigDecimal total, LocalDate lentOn) {
    BigDecimal sum = plan.stream()
        .map(item -> money(item.getAmount()))
        .reduce(ZERO, BigDecimal::add);
    if (sum.compareTo(money(total)) != 0) {
      throw new InvalidLoanPlanException(Constants.LOAN_PLAN_TOTAL_MISMATCH
          .replaceFirst("\\{}", sum.toPlainString())
          .replaceFirst("\\{}", money(total).toPlainString()));
    }
    if (plan.stream().anyMatch(item -> item.getDueDate().isBefore(lentOn))) {
      throw new InvalidLoanPlanException(Constants.LOAN_INSTALLMENT_BEFORE_LENT_ON);
    }
    List<LoanInstallmentRequest> sorted = plan.stream()
        .sorted(Comparator.comparing(LoanInstallmentRequest::getDueDate))
        .toList();
    List<LoanInstallment> installments = new ArrayList<>(sorted.size());
    for (int i = 0; i < sorted.size(); i++) {
      installments.add(new LoanInstallment(null, loanId, i + 1, sorted.get(i).getDueDate(),
          money(sorted.get(i).getAmount())));
    }
    return installments;
  }

  /**
   * Resuelve un préstamo contra un día: qué cubre lo devuelto, qué falta y qué está vencido.
   *
   * <p>Una cuota está vencida cuando su fecha ya pasó, no el mismo día: el día que vence
   * todavía se puede pagar a tiempo. Un préstamo cerrado no tiene nada vencido ni próxima
   * cuota, porque ya no se espera nada de él.</p>
   *
   * @param loan         préstamo guardado
   * @param installments su plan, en cualquier orden
   * @param payments     lo devuelto, en el orden en que se quiera mostrar
   * @param today        día contra el que se resuelve, en la zona de los usuarios
   * @return el préstamo con sus cifras de ese día
   */
  public static LoanDetail statement(Loan loan, List<LoanInstallment> installments,
                                     List<LoanPayment> payments, LocalDate today) {
    BigDecimal total = money(loan.getPrincipal().add(loan.getInterest()));
    BigDecimal paid = payments.stream()
        .map(LoanPayment::getAmount)
        .reduce(ZERO, BigDecimal::add);
    boolean closed = loan.getClosedOn() != null;

    BigDecimal available = paid;
    BigDecimal overdue = ZERO;
    LocalDate nextDueDate = null;
    BigDecimal nextDueAmount = null;
    List<LoanInstallmentDetail> details = new ArrayList<>(installments.size());
    for (LoanInstallment installment : installments.stream()
        .sorted(Comparator.comparing(LoanInstallment::getNumber)).toList()) {
      BigDecimal covered = available.min(installment.getAmount()).max(ZERO);
      available = available.subtract(covered);
      BigDecimal missing = installment.getAmount().subtract(covered);
      boolean late = installment.getDueDate().isBefore(today);
      LoanInstallmentStatus status;
      if (missing.signum() == 0) {
        status = LoanInstallmentStatus.PAID;
      } else if (late) {
        status = LoanInstallmentStatus.OVERDUE;
      } else if (covered.signum() > 0) {
        status = LoanInstallmentStatus.PARTIAL;
      } else {
        status = LoanInstallmentStatus.PENDING;
      }
      if (missing.signum() > 0 && !closed) {
        if (late) {
          overdue = overdue.add(missing);
        }
        if (nextDueDate == null) {
          nextDueDate = installment.getDueDate();
          nextDueAmount = missing;
        }
      }
      details.add(new LoanInstallmentDetail(installment.getId(), installment.getNumber(),
          installment.getDueDate(), installment.getAmount(), covered, status));
    }

    BigDecimal outstanding = total.subtract(paid).max(ZERO);
    LoanStatus status;
    if (outstanding.signum() == 0) {
      status = LoanStatus.PAID;
    } else if (closed) {
      status = LoanStatus.CLOSED;
    } else if (overdue.signum() > 0) {
      status = LoanStatus.OVERDUE;
    } else {
      status = LoanStatus.ACTIVE;
    }
    return new LoanDetail(loan, total, paid, outstanding, overdue, status, nextDueDate,
        nextDueAmount, details, payments);
  }

  private static int rank(LoanStatus status) {
    return switch (status) {
      case OVERDUE -> 0;
      case ACTIVE -> 1;
      case PAID, CLOSED -> 2;
    };
  }
}
