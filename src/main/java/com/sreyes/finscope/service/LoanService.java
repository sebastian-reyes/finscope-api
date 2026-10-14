package com.sreyes.finscope.service;

import com.sreyes.finscope.api.model.SaveLoanPaymentRequest;
import com.sreyes.finscope.api.model.SaveLoanRequest;
import com.sreyes.finscope.api.model.UpdateLoanRequest;
import com.sreyes.finscope.model.query.LoanDetail;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio para el dinero que el usuario prestó a otras personas.
 * Toda operación parte del identificador del propietario, de modo que un préstamo ajeno se
 * comporta igual que uno inexistente.
 *
 * Ninguna operación escribe transacciones: prestar no es gastar ni cobrar es ingresar. Cada
 * una devuelve el préstamo entero resuelto contra el día de hoy, porque lo que se quiere ver
 * después de registrar un pago es cuánto falta, no el pago.
 */
public interface LoanService {

  /**
   * Obtiene todos los préstamos del usuario, con lo vencido primero.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con sus préstamos resueltos contra hoy
   */
  Flux<LoanDetail> findLoans(Long userId);

  /**
   * Obtiene un préstamo del usuario.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del préstamo
   * @return el préstamo resuelto contra hoy
   */
  Mono<LoanDetail> findLoan(Long userId, Long id);

  /**
   * Registra un préstamo junto a su plan de cuotas.
   *
   * @param userId  identificador del usuario propietario
   * @param request datos del préstamo y su plan
   * @return el préstamo creado
   */
  Mono<LoanDetail> createLoan(Long userId, SaveLoanRequest request);

  /**
   * Modifica un préstamo. Solo se aplican los campos presentes.
   *
   * @param userId  identificador del usuario propietario
   * @param id      identificador del préstamo
   * @param request cambios a aplicar
   * @return el préstamo tal y como queda
   */
  Mono<LoanDetail> updateLoan(Long userId, Long id, UpdateLoanRequest request);

  /**
   * Elimina un préstamo con su plan y sus pagos.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del préstamo
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteLoan(Long userId, Long id);

  /**
   * Registra dinero que el deudor devolvió.
   *
   * @param userId  identificador del usuario propietario
   * @param id      identificador del préstamo
   * @param request fecha, importe y nota del pago
   * @return el préstamo tal y como queda
   */
  Mono<LoanDetail> addPayment(Long userId, Long id, SaveLoanPaymentRequest request);

  /**
   * Elimina un pago registrado por error.
   *
   * @param userId    identificador del usuario propietario
   * @param id        identificador del préstamo
   * @param paymentId identificador del pago
   * @return el préstamo tal y como queda
   */
  Mono<LoanDetail> deletePayment(Long userId, Long id, Long paymentId);
}
