package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.LoanInstallment;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link LoanInstallment}.
 * Proporciona operaciones reactivas sobre la tabla `loan_installments`, el plan de
 * devolución de cada préstamo.
 *
 * El plan se reemplaza siempre entero: cambiar una cuota cambia la numeración y lo que
 * cubre cada pago, así que editarlas una a una no tiene un significado claro.
 */
@Repository
public interface LoanInstallmentRepository extends R2dbcRepository<LoanInstallment, Long> {

  /**
   * Obtiene el plan de un préstamo en orden de cuota.
   *
   * @param loanId identificador del préstamo
   * @return flujo reactivo con sus cuotas
   */
  Flux<LoanInstallment> findByLoanIdOrderByNumber(Long loanId);

  /**
   * Obtiene las cuotas de todos los préstamos de un usuario en una sola consulta, en orden
   * de préstamo y de cuota. Es lo que permite listar sin una consulta por préstamo.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con las cuotas
   */
  @Query("""
      SELECT i.*
      FROM loan_installments i
      INNER JOIN loans l ON l.id_loan = i.loan_id
      WHERE l.user_id = :userId
      ORDER BY i.loan_id, i.number
      """)
  Flux<LoanInstallment> findByUserId(Long userId);

  /**
   * Elimina el plan entero de un préstamo, como paso previo a guardar el nuevo.
   *
   * @param loanId identificador del préstamo
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteByLoanId(Long loanId);
}
