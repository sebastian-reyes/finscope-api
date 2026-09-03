package com.sreyes.finscope.service;

import com.sreyes.finscope.model.query.BudgetProgress;
import java.math.BigDecimal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio para la gestión de los presupuestos mensuales del usuario.
 * Toda operación parte del identificador del propietario, de modo que un presupuesto ajeno
 * se comporta igual que uno inexistente.
 *
 * El presupuesto siempre viaja con su avance: es lo que se va a mirar, y calcularlo por
 * separado obligaría al cliente a correlacionar dos listas por categoría.
 */
public interface BudgetService {

  /**
   * Obtiene los presupuestos del usuario para un mes junto a lo gastado en cada categoría.
   *
   * @param userId identificador del usuario propietario
   * @param month  mes solicitado, entre 1 y 12
   * @param year   año solicitado
   * @return flujo reactivo con los presupuestos del mes, en orden alfabético de categoría
   */
  Flux<BudgetProgress> findBudgets(Long userId, Integer month, Integer year);

  /**
   * Fija el presupuesto de una categoría para un mes.
   *
   * @param userId     identificador del usuario propietario
   * @param categoryId identificador de la categoría a presupuestar, que debe admitir egresos
   * @param month      mes al que se aplica
   * @param year       año al que se aplica
   * @param amount     importe presupuestado
   * @return el presupuesto creado junto a su avance
   */
  Mono<BudgetProgress> createBudget(Long userId, Long categoryId, Integer month, Integer year,
                                    BigDecimal amount);

  /**
   * Cambia el importe de un presupuesto del usuario.
   * La categoría y el mes no se tocan: son lo que identifica al presupuesto.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del presupuesto
   * @param amount importe nuevo
   * @return el presupuesto actualizado junto a su avance
   */
  Mono<BudgetProgress> updateBudget(Long userId, Long id, BigDecimal amount);

  /**
   * Retira el presupuesto de una categoría. Los movimientos que tuviera no se tocan.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del presupuesto
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteBudget(Long userId, Long id);

  /**
   * Copia al mes destino los presupuestos del mes origen, sin pisar los que el destino ya
   * tuviera.
   *
   * @param userId      identificador del usuario propietario
   * @param sourceMonth mes del que se copia
   * @param sourceYear  año del que se copia
   * @param month       mes al que se copia
   * @param year        año al que se copia
   * @return el mes destino completo tal y como queda tras la copia
   */
  Flux<BudgetProgress> copyBudgets(Long userId, Integer sourceMonth, Integer sourceYear,
                                   Integer month, Integer year);
}
