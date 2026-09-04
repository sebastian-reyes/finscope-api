package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Budget;
import com.sreyes.finscope.model.query.BudgetProgress;
import com.sreyes.finscope.util.query.RecurringSql;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link Budget}.
 * Proporciona operaciones reactivas sobre la tabla `budgets`, que guarda cuánto piensa
 * gastar el usuario en cada categoría durante un mes.
 *
 * El avance no se guarda: se calcula al leer, sumando los egresos de la categoría dentro
 * del mes. Guardarlo obligaría a rehacerlo cada vez que se registra, se corrige o se borra
 * un movimiento, y bastaría con que fallara una de esas veces para que el plan y la
 * realidad dejaran de cuadrar sin que nadie se enterase.
 */
@Repository
public interface BudgetRepository extends R2dbcRepository<Budget, Long> {

  /**
   * Origen común de las consultas de avance.
   *
   * <p>El gasto llega por una unión externa contra los egresos ya agrupados por categoría,
   * y no por una subconsulta correlacionada fila a fila: así el rango de fechas se recorre
   * una sola vez y se apoya en el índice de transacciones por usuario y fecha. Es externa
   * porque un presupuesto sin ningún gasto todavía debe aparecer, con su avance a cero.</p>
   *
   * <p>Solo suman los egresos, que es la misma cifra que da el desglose por categoría del
   * resumen. Si aquí se restaran además los ingresos de la categoría, la barra de avance y
   * el gráfico de reparto contarían cosas distintas del mismo periodo.</p>
   *
   * <p>Lo comprometido llega por una tercera unión: son los movimientos fijos de la
   * categoría que vencen ese mes y todavía no se han confirmado ni omitido. Es dinero que
   * aún no figura en lo gastado pero que ya tiene dueño, y sin él un presupuesto de 400 con
   * 120 gastados aparenta 280 libres cuando el internet de 180 sigue sin pagarse. Solo
   * cuentan los egresos, por lo mismo que arriba: un sueldo fijo no compromete un
   * presupuesto de gasto.</p>
   *
   * <p>La condición de vencimiento es la misma constante que usa el listado de fijos. Si
   * cada uno tuviera su copia, la pantalla de fijos diría que el internet vence este mes y
   * esta barra no lo estaría contando, sin forma de saber cuál de las dos miente.</p>
   */
  String PROGRESS_SELECT = """
      SELECT b.id_budget AS budget_id,
             b.category_id AS budget_category_id,
             c.name_category AS budget_category_name,
             b.month AS budget_month,
             b.year AS budget_year,
             b.amount AS budget_amount,
             COALESCE(s.spent, 0) AS budget_spent,
             COALESCE(k.committed, 0) AS budget_committed
      FROM budgets b
      INNER JOIN categories c ON c.id_category = b.category_id
      LEFT JOIN (SELECT t.category_id AS category_id,
                        SUM(t.amount) AS spent
                 FROM transactions t
                 INNER JOIN transaction_types tt
                         ON tt.id_transaction_type = t.transaction_type_id
                 WHERE t.user_id = :userId
                   AND tt.code = 'EXPENSE'
                   AND t.date >= :periodStart
                   AND t.date <= :periodEnd
                 GROUP BY t.category_id) s ON s.category_id = b.category_id
      LEFT JOIN (SELECT r.category_id AS category_id,
                        SUM(r.amount) AS committed
                 FROM recurring_transactions r
                 INNER JOIN transaction_types rt
                         ON rt.id_transaction_type = r.transaction_type_id
                 WHERE r.user_id = :userId
                   AND rt.code = 'EXPENSE'
      """
      + "             AND " + RecurringSql.DUE_IN_PERIOD + "\n"
      + """
                   AND NOT EXISTS (SELECT 1
                                   FROM recurring_skips k2
                                   WHERE k2.recurring_id = r.id_recurring
                                     AND k2.month = :month
                                     AND k2.year = :year)
                   AND NOT EXISTS (SELECT 1
                                   FROM transactions t2
                                   WHERE t2.recurring_id = r.id_recurring
                                     AND t2.date >= :periodStart
                                     AND t2.date <= :periodEnd)
                 GROUP BY r.category_id) k ON k.category_id = b.category_id
      """;

  /**
   * Obtiene los presupuestos del usuario para un mes junto a su avance.
   * El rango de fechas llega ya resuelto para que el mes se interprete igual aquí que en
   * el listado y en los resúmenes, en lugar de volver a calcularse dentro de la consulta.
   *
   * @param userId      identificador del usuario propietario
   * @param month       mes solicitado, entre 1 y 12
   * @param year        año solicitado
   * @param periodStart primer instante del mes, inclusivo
   * @param periodEnd   último instante del mes, inclusivo
   * @return flujo reactivo con los presupuestos del mes, en orden alfabético de categoría
   */
  @Query(PROGRESS_SELECT + """
      WHERE b.user_id = :userId AND b.month = :month AND b.year = :year
      ORDER BY LOWER(c.name_category)
      """)
  Flux<BudgetProgress> findProgressByPeriod(Long userId, Integer month, Integer year,
                                            LocalDateTime periodStart, LocalDateTime periodEnd);

  /**
   * Obtiene un único presupuesto del usuario junto a su avance.
   * Es la misma proyección que devuelve el listado, para que crear o cambiar el importe
   * responda exactamente con la forma con la que después se lista.
   *
   * <p>El mes llega aparte aunque el presupuesto ya lo lleve dentro, porque lo
   * comprometido se resuelve contra el mes natural y no contra la fila: las omisiones de
   * los fijos se guardan por mes y año, no por rango de fechas.</p>
   *
   * @param userId      identificador del usuario propietario
   * @param id          identificador del presupuesto
   * @param month       mes del presupuesto, entre 1 y 12
   * @param year        año del presupuesto
   * @param periodStart primer instante de su mes, inclusivo
   * @param periodEnd   último instante de su mes, inclusivo
   * @return el presupuesto con su avance envuelto en Mono
   */
  @Query(PROGRESS_SELECT + """
      WHERE b.user_id = :userId AND b.id_budget = :id
      """)
  Mono<BudgetProgress> findProgressById(Long userId, Long id, Integer month, Integer year,
                                        LocalDateTime periodStart, LocalDateTime periodEnd);

  /**
   * Busca un presupuesto del usuario por su identificador.
   * Acota por propietario para que un presupuesto ajeno se comporte igual que uno
   * inexistente y nadie pueda alcanzarlo conociendo su identificador.
   *
   * @param id     identificador del presupuesto
   * @param userId identificador del usuario propietario
   * @return el presupuesto encontrado envuelto en Mono
   */
  Mono<Budget> findByIdAndUserId(Long id, Long userId);

  /**
   * Busca el presupuesto que el usuario tiene para una categoría en un mes concreto.
   *
   * @param userId     identificador del usuario propietario
   * @param categoryId identificador de la categoría presupuestada
   * @param month      mes solicitado
   * @param year       año solicitado
   * @return el presupuesto encontrado envuelto en Mono
   */
  @Query("""
      SELECT *
      FROM budgets
      WHERE user_id = :userId
        AND category_id = :categoryId
        AND month = :month
        AND year = :year
      """)
  Mono<Budget> findByCategoryAndPeriod(Long userId, Long categoryId, Integer month, Integer year);

  /**
   * Fija el presupuesto de una categoría si esa categoría todavía no lo tiene en ese mes.
   * Se apoya en la restricción de unicidad de la base de datos en lugar de comprobar antes
   * si está libre, porque entre la comprobación y la inserción podría colarse otra petición
   * del mismo usuario presupuestando esa misma categoría.
   *
   * @param userId     identificador del usuario propietario
   * @param categoryId identificador de la categoría a presupuestar
   * @param month      mes al que se aplica
   * @param year       año al que se aplica
   * @param amount     importe presupuestado
   * @return número de filas insertadas, cero si la categoría ya tenía presupuesto ese mes
   */
  @Modifying
  @Query("""
      INSERT INTO budgets (user_id, category_id, month, year, amount)
      VALUES (:userId, :categoryId, :month, :year, :amount)
      ON CONFLICT DO NOTHING
      """)
  Mono<Long> insertIfAbsent(Long userId, Long categoryId, Integer month, Integer year,
                            BigDecimal amount);

  /**
   * Copia al mes destino los presupuestos que el usuario tuviera en el mes origen.
   * Lo que el destino ya tenga manda y no se pisa: el conflicto se ignora en lugar de
   * actualizar, de modo que repetir la copia no cambia nada la segunda vez y nunca
   * sobrescribe un importe que el usuario acabe de ajustar a mano.
   *
   * @param userId      identificador del usuario propietario
   * @param sourceMonth mes del que se copia
   * @param sourceYear  año del que se copia
   * @param month       mes al que se copia
   * @param year        año al que se copia
   * @return número de presupuestos copiados
   */
  @Modifying
  @Query("""
      INSERT INTO budgets (user_id, category_id, month, year, amount)
      SELECT b.user_id, b.category_id, :month, :year, b.amount
      FROM budgets b
      WHERE b.user_id = :userId AND b.month = :sourceMonth AND b.year = :sourceYear
      ON CONFLICT DO NOTHING
      """)
  Mono<Long> copyPeriod(Long userId, Integer sourceMonth, Integer sourceYear, Integer month,
                        Integer year);
}
