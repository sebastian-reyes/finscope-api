package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.RecurringTransaction;
import com.sreyes.finscope.model.query.RecurringDetail;
import com.sreyes.finscope.util.query.RecurringSql;
import java.time.LocalDateTime;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link RecurringTransaction}.
 * Proporciona operaciones reactivas sobre la tabla `recurring_transactions`, que guarda las
 * plantillas de los movimientos que se repiten.
 *
 * La plantilla vive por encima de los meses y su estado en cada uno se resuelve al leer: la
 * consulta trae los hechos —si vence, si el mes está omitido y con qué movimiento se
 * confirmó— y el servicio decide a partir de ellos si está pendiente, vencido o pagado.
 * Lo único que el servicio pone de su parte es qué día es hoy, que es lo que separa un
 * pendiente de un vencido y no tiene por qué saberlo una consulta.
 */
@Repository
public interface RecurringTransactionRepository
    extends R2dbcRepository<RecurringTransaction, Long> {

  /**
   * Origen común de las consultas de estado.
   *
   * <p>Las dos uniones que resuelven el mes son externas a propósito: un fijo sin omitir y
   * sin confirmar todavía tiene que aparecer, y es justo el que hay que pagar.</p>
   *
   * <p>El mes y el año se devuelven tal cual se piden, con un CAST explícito porque un
   * parámetro suelto en la lista de selección no le dice a Postgres de qué tipo es. Viajan
   * en la propia fila para que la respuesta diga siempre contra qué mes se resolvió y no
   * haya que arrastrarlo aparte hasta el mapper.</p>
   */
  String DETAIL_SELECT = """
      SELECT r.id_recurring AS recurring_id,
             r.category_id AS recurring_category_id,
             c.name_category AS recurring_category_name,
             r.transaction_type_id AS recurring_type_id,
             tt.code AS recurring_type_code,
             r.description AS recurring_description,
             r.amount AS recurring_amount,
             r.day_of_month AS recurring_day_of_month,
             r.every_months AS recurring_every_months,
             r.start_month AS recurring_start_month,
             r.start_year AS recurring_start_year,
             r.active AS recurring_active,
             CAST(:month AS integer) AS recurring_month,
             CAST(:year AS integer) AS recurring_year,
      """
      + "       " + RecurringSql.DUE_IN_PERIOD + " AS recurring_due,\n"
      + """
             (s.id_recurring_skip IS NOT NULL) AS recurring_skipped,
             t.id_transaction AS recurring_transaction_id,
             t.amount AS recurring_paid_amount,
             t.date AS recurring_paid_date
      FROM recurring_transactions r
      INNER JOIN categories c ON c.id_category = r.category_id
      INNER JOIN transaction_types tt ON tt.id_transaction_type = r.transaction_type_id
      LEFT JOIN recurring_skips s ON s.recurring_id = r.id_recurring
                                 AND s.month = :month
                                 AND s.year = :year
      LEFT JOIN transactions t ON t.recurring_id = r.id_recurring
                              AND t.date >= :periodStart
                              AND t.date <= :periodEnd
      """;

  /**
   * Obtiene todas las plantillas del usuario resueltas contra un mes.
   *
   * <p>Se devuelven todas, activas y pausadas, venzan o no ese mes: la pantalla de gestión
   * necesita verlas siempre y quien solo quiera el checklist filtra por estado. Filtrar
   * aquí obligaría a una segunda consulta para lo mismo.</p>
   *
   * <p>El orden es el del calendario, por día previsto, porque así es como se lee un
   * checklist: lo que vence antes, antes.</p>
   *
   * @param userId      identificador del usuario propietario
   * @param month       mes contra el que se resuelve, entre 1 y 12
   * @param year        año contra el que se resuelve
   * @param periodStart primer instante del mes, inclusivo
   * @param periodEnd   último instante del mes, inclusivo
   * @return flujo reactivo con las plantillas del usuario y lo que se sabe de ese mes
   */
  @Query(DETAIL_SELECT + """
      WHERE r.user_id = :userId
      ORDER BY r.day_of_month, LOWER(r.description)
      """)
  Flux<RecurringDetail> findDetailsByPeriod(Long userId, Integer month, Integer year,
                                            LocalDateTime periodStart, LocalDateTime periodEnd);

  /**
   * Obtiene una plantilla del usuario resuelta contra un mes.
   * Es la misma proyección que devuelve el listado, para que confirmar u omitir responda
   * exactamente con la forma con la que después se lista.
   *
   * @param userId      identificador del usuario propietario
   * @param id          identificador de la plantilla
   * @param month       mes contra el que se resuelve
   * @param year        año contra el que se resuelve
   * @param periodStart primer instante del mes, inclusivo
   * @param periodEnd   último instante del mes, inclusivo
   * @return la plantilla con lo que se sabe de ese mes envuelta en Mono
   */
  @Query(DETAIL_SELECT + """
      WHERE r.user_id = :userId AND r.id_recurring = :id
      """)
  Mono<RecurringDetail> findDetailById(Long userId, Long id, Integer month, Integer year,
                                       LocalDateTime periodStart, LocalDateTime periodEnd);

  /**
   * Busca una plantilla del usuario por su identificador.
   * Acota por propietario para que una plantilla ajena se comporte igual que una
   * inexistente y nadie pueda alcanzarla conociendo su identificador.
   *
   * @param id     identificador de la plantilla
   * @param userId identificador del usuario propietario
   * @return la plantilla encontrada envuelta en Mono
   */
  Mono<RecurringTransaction> findByIdAndUserId(Long id, Long userId);

  /**
   * Traslada a otra categoría las plantillas que apuntaban a una que se elimina.
   * Es el mismo trato que reciben las transacciones: perder el alquiler por reordenar el
   * catálogo de categorías sería desproporcionado.
   *
   * @param userId   identificador del usuario propietario
   * @param sourceId categoría que se elimina
   * @param targetId categoría de reserva que las recibe
   * @return número de plantillas reasignadas
   */
  @Modifying
  @Query("""
      UPDATE recurring_transactions
      SET category_id = :targetId
      WHERE user_id = :userId AND category_id = :sourceId
      """)
  Mono<Long> reassignCategory(Long userId, Long sourceId, Long targetId);
}
