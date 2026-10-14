package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.LoanPayment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link LoanPayment}.
 * Proporciona operaciones reactivas sobre la tabla `loan_payments`, lo que cada deudor fue
 * devolviendo.
 */
@Repository
public interface LoanPaymentRepository extends R2dbcRepository<LoanPayment, Long> {

  /**
   * Obtiene lo devuelto de un préstamo, del pago más reciente al más antiguo.
   *
   * @param loanId identificador del préstamo
   * @return flujo reactivo con sus pagos
   */
  Flux<LoanPayment> findByLoanIdOrderByPaidOnDescIdDesc(Long loanId);

  /**
   * Obtiene los pagos de todos los préstamos de un usuario en una sola consulta.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con los pagos, del más reciente al más antiguo
   */
  @Query("""
      SELECT p.*
      FROM loan_payments p
      INNER JOIN loans l ON l.id_loan = p.loan_id
      WHERE l.user_id = :userId
      ORDER BY p.loan_id, p.paid_on DESC, p.id_loan_payment DESC
      """)
  Flux<LoanPayment> findByUserId(Long userId);

  /**
   * Busca un pago dentro de un préstamo. Acota por préstamo para que el identificador de un
   * pago no alcance el de otro préstamo, que podría ser de otro usuario.
   *
   * @param id     identificador del pago
   * @param loanId identificador del préstamo
   * @return el pago encontrado envuelto en Mono
   */
  Mono<LoanPayment> findByIdAndLoanId(Long id, Long loanId);
}
