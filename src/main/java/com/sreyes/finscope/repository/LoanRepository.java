package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Loan;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link Loan}.
 * Proporciona operaciones reactivas sobre la tabla `loans`, que guarda el dinero que el
 * usuario prestó a otras personas.
 *
 * Lo devuelto y lo que falta no se guardan aquí: salen de comparar las cuotas con los
 * pagos al leer, de modo que borrar un pago mal apuntado no deja ninguna cifra desfasada.
 */
@Repository
public interface LoanRepository extends R2dbcRepository<Loan, Long> {

  /**
   * Obtiene todos los préstamos del usuario. El orden con el que se muestran depende del
   * estado de hoy, que se calcula después, así que aquí no se ordena.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con sus préstamos
   */
  Flux<Loan> findByUserId(Long userId);

  /**
   * Busca un préstamo del usuario por su identificador.
   * Acota por propietario para que un préstamo ajeno se comporte igual que uno inexistente.
   *
   * @param id     identificador del préstamo
   * @param userId identificador del usuario propietario
   * @return el préstamo encontrado envuelto en Mono
   */
  Mono<Loan> findByIdAndUserId(Long id, Long userId);
}
