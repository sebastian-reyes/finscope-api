package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.query.BudgetLimitNotice;
import com.sreyes.finscope.model.query.DueLoanNotice;
import com.sreyes.finscope.model.query.DueRecurringNotice;
import com.sreyes.finscope.model.query.NotificationSettings;
import com.sreyes.finscope.util.query.RecurringSql;
import io.r2dbc.spi.Readable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Consultas de los avisos: preferencias, qué toca avisar y qué se avisó ya.
 *
 * <p>Va con {@link DatabaseClient} y no como repositorio derivado porque ninguna de estas
 * consultas devuelve una entidad, y porque las de la tarea programada son las únicas de la
 * API que recorren a <strong>todos</strong> los usuarios a la vez. Todas las demás acotan por
 * el `user_id` del token; estas no tienen token, y por eso cada fila devuelta trae su propio
 * `user_id` y el servicio manda cada aviso solo a los dispositivos de ese usuario.</p>
 *
 * <p>Las dos consultas de candidatos solo miran usuarios con algún dispositivo suscrito y con
 * ese tipo de aviso encendido: lo que no se va a poder mandar no tiene sentido calcularlo.
 * Las columnas se leen por nombre a mano, sin proyección, así que aquí no aplica la trampa
 * de los registros cuyos componentes se llaman como las propiedades de una entidad.</p>
 */
@Repository
@RequiredArgsConstructor
public class NotificationRepository {

  /**
   * Fijos pendientes que vencen en un día concreto, de los usuarios avisables.
   *
   * <p>La regla de si vence ese mes es {@link RecurringSql#DUE_IN_PERIOD}, la misma del
   * listado de fijos y de lo comprometido de los presupuestos: el aviso no puede decir que el
   * alquiler vence mañana si la pantalla dice que este mes no toca. El día se recorta al mes
   * igual que en la pantalla, así que un fijo del 31 avisa del 28 en febrero.</p>
   *
   * <p>Lo omitido y lo ya confirmado dentro del mes no se avisa: el aviso es para lo que
   * falta hacer, no un calendario.</p>
   */
  private static final String RECURRING_DUE_ON = """
      SELECT r.user_id, r.id_recurring, r.description, r.amount, r.currency, tt.code
      FROM recurring_transactions r
      INNER JOIN transaction_types tt ON tt.id_transaction_type = r.transaction_type_id
      LEFT JOIN notification_preferences p ON p.user_id = r.user_id
      WHERE\s"""
      + RecurringSql.DUE_IN_PERIOD + "\n"
      + """
        AND LEAST(r.day_of_month, :lastDay) = :day
        AND COALESCE(p.recurring_due, true)
        AND EXISTS (SELECT 1 FROM push_subscriptions ps WHERE ps.user_id = r.user_id)
        AND NOT EXISTS (SELECT 1
                        FROM recurring_skips s
                        WHERE s.recurring_id = r.id_recurring
                          AND s.month = :month
                          AND s.year = :year)
        AND NOT EXISTS (SELECT 1
                        FROM transactions t
                        WHERE t.recurring_id = r.id_recurring
                          AND t.date >= :periodStart
                          AND t.date <= :periodEnd)
      ORDER BY r.user_id, r.id_recurring
      """;

  /**
   * Presupuestos de un mes cuyo gasto ya llegó a una fracción del importe, de los usuarios
   * avisables.
   *
   * <p>Lo gastado se cuenta como en la barra de la pantalla: solo egresos, de esa categoría,
   * de ese mes y en la moneda del presupuesto. Lo comprometido por los fijos no entra: el
   * aviso dice cuánto se ha gastado de verdad, y avisar de que un presupuesto se pasa por un
   * alquiler que todavía no se ha pagado sería una alarma que el usuario no puede comprobar
   * en ningún movimiento.</p>
   */
  private static final String BUDGETS_REACHING = """
      SELECT b.user_id, b.id_budget, c.name_category, b.currency, b.amount, s.spent
      FROM budgets b
      INNER JOIN categories c ON c.id_category = b.category_id
      INNER JOIN (SELECT t.user_id, t.category_id, t.currency, SUM(t.amount) AS spent
                  FROM transactions t
                  INNER JOIN transaction_types tt
                          ON tt.id_transaction_type = t.transaction_type_id
                  WHERE tt.code = 'EXPENSE'
                    AND t.date >= :periodStart
                    AND t.date <= :periodEnd
                    AND t.user_id IN (SELECT ps.user_id FROM push_subscriptions ps)
                  GROUP BY t.user_id, t.category_id, t.currency) s
              ON s.user_id = b.user_id
             AND s.category_id = b.category_id
             AND s.currency = b.currency
      LEFT JOIN notification_preferences p ON p.user_id = b.user_id
      WHERE b.month = :month
        AND b.year = :year
        AND b.amount > 0
        AND COALESCE(p.budget_limit, true)
        AND s.spent >= b.amount * :ratio
      ORDER BY b.user_id, b.id_budget
      """;

  /**
   * Cuotas de préstamos sin cubrir que vencen dentro de un rango de días, de los usuarios
   * avisables.
   *
   * <p>Lo devuelto se aplica en orden de cuota, igual que en
   * {@link com.sreyes.finscope.util.rules.LoanRules}: una cuota sigue pendiente mientras lo
   * acumulado hasta ella supera lo devuelto, y lo que falta de ella es esa diferencia, sin
   * pasar de su importe. Así el aviso dice «faltan 50» de una cuota a medias, igual que la
   * pantalla.</p>
   *
   * <p>Las ventanas se calculan solo para los préstamos con alguna cuota en el rango, que es
   * lo que deja usar el índice por fecha en lugar de recorrer todas las cuotas.</p>
   */
  private static final String LOAN_INSTALLMENTS_DUE = """
      SELECT l.user_id, l.id_loan, l.borrower_name, l.currency,
             i.id_loan_installment, i.number, i.installment_count, i.due_date,
             LEAST(i.amount, i.cumulative - COALESCE(p.paid, 0)) AS pending
      FROM loans l
      INNER JOIN (SELECT li.id_loan_installment, li.loan_id, li.number, li.due_date, li.amount,
                         SUM(li.amount) OVER (PARTITION BY li.loan_id ORDER BY li.number)
                             AS cumulative,
                         COUNT(*) OVER (PARTITION BY li.loan_id) AS installment_count
                  FROM loan_installments li
                  WHERE li.loan_id IN (SELECT d.loan_id
                                       FROM loan_installments d
                                       WHERE d.due_date BETWEEN :from AND :to)) i
              ON i.loan_id = l.id_loan
      LEFT JOIN (SELECT lp.loan_id, SUM(lp.amount) AS paid
                 FROM loan_payments lp
                 GROUP BY lp.loan_id) p
             ON p.loan_id = l.id_loan
      LEFT JOIN notification_preferences np ON np.user_id = l.user_id
      WHERE i.due_date BETWEEN :from AND :to
        AND i.cumulative > COALESCE(p.paid, 0)
        AND l.closed_on IS NULL
        AND COALESCE(np.loan_due, true)
        AND EXISTS (SELECT 1 FROM push_subscriptions ps WHERE ps.user_id = l.user_id)
      ORDER BY l.user_id, i.due_date, i.id_loan_installment
      """;

  private final DatabaseClient databaseClient;

  /**
   * Obtiene las preferencias de un usuario, con todo encendido si nunca las cambió.
   *
   * @param userId identificador del usuario
   * @return sus preferencias
   */
  public Mono<NotificationSettings> findSettings(Long userId) {
    return databaseClient.sql("""
            SELECT recurring_due, budget_limit, loan_due
            FROM notification_preferences
            WHERE user_id = :userId
            """)
        .bind("userId", userId)
        .map(NotificationRepository::toSettings)
        .one()
        .defaultIfEmpty(NotificationSettings.DEFAULTS);
  }

  /**
   * Cambia las preferencias de un usuario, creando su fila si no la tenía.
   * Lo que llega nulo se deja como estaba, igual que en el resto de modificaciones parciales.
   *
   * @param userId       identificador del usuario
   * @param recurringDue avisos de fijos, o nulo para no tocarlo
   * @param budgetLimit  avisos de presupuestos, o nulo para no tocarlo
   * @param loanDue      avisos de préstamos por cobrar, o nulo para no tocarlo
   * @return las preferencias tal y como quedan
   */
  public Mono<NotificationSettings> saveSettings(Long userId, Boolean recurringDue,
                                                 Boolean budgetLimit, Boolean loanDue) {
    DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
            INSERT INTO notification_preferences (user_id, recurring_due, budget_limit, loan_due)
            VALUES (:userId, COALESCE(:recurringDue, true), COALESCE(:budgetLimit, true),
                    COALESCE(:loanDue, true))
            ON CONFLICT (user_id) DO UPDATE
            SET recurring_due = COALESCE(:recurringDue, notification_preferences.recurring_due),
                budget_limit = COALESCE(:budgetLimit, notification_preferences.budget_limit),
                loan_due = COALESCE(:loanDue, notification_preferences.loan_due)
            RETURNING recurring_due, budget_limit, loan_due
            """)
        .bind("userId", userId);
    spec = recurringDue == null
        ? spec.bindNull("recurringDue", Boolean.class)
        : spec.bind("recurringDue", recurringDue);
    spec = budgetLimit == null
        ? spec.bindNull("budgetLimit", Boolean.class)
        : spec.bind("budgetLimit", budgetLimit);
    spec = loanDue == null
        ? spec.bindNull("loanDue", Boolean.class)
        : spec.bind("loanDue", loanDue);
    return spec
        .map(NotificationRepository::toSettings)
        .one();
  }

  /**
   * Obtiene los fijos pendientes que vencen en una fecha, de todos los usuarios avisables.
   *
   * @param date        fecha de vencimiento buscada
   * @param periodStart primer instante del mes de esa fecha
   * @param periodEnd   último instante del mes de esa fecha
   * @return flujo con los fijos de los que avisar
   */
  public Flux<DueRecurringNotice> findRecurringDueOn(LocalDate date, LocalDateTime periodStart,
                                                     LocalDateTime periodEnd) {
    return databaseClient.sql(RECURRING_DUE_ON)
        .bind("month", date.getMonthValue())
        .bind("year", date.getYear())
        .bind("day", date.getDayOfMonth())
        .bind("lastDay", date.lengthOfMonth())
        .bind("periodStart", periodStart)
        .bind("periodEnd", periodEnd)
        .map(row -> new DueRecurringNotice(
            row.get("user_id", Long.class),
            row.get("id_recurring", Long.class),
            row.get("description", String.class),
            row.get("amount", BigDecimal.class),
            row.get("currency", String.class),
            row.get("code", String.class)))
        .all();
  }

  /**
   * Obtiene los presupuestos de un mes cuyo gasto ya llegó a una fracción del importe.
   *
   * @param month       mes del presupuesto
   * @param year        año del presupuesto
   * @param periodStart primer instante del mes
   * @param periodEnd   último instante del mes
   * @param ratio       fracción del importe a partir de la cual se avisa
   * @return flujo con los presupuestos de los que avisar
   */
  public Flux<BudgetLimitNotice> findBudgetsReaching(int month, int year,
                                                     LocalDateTime periodStart,
                                                     LocalDateTime periodEnd, double ratio) {
    return databaseClient.sql(BUDGETS_REACHING)
        .bind("month", month)
        .bind("year", year)
        .bind("periodStart", periodStart)
        .bind("periodEnd", periodEnd)
        .bind("ratio", BigDecimal.valueOf(ratio))
        .map(row -> new BudgetLimitNotice(
            row.get("user_id", Long.class),
            row.get("id_budget", Long.class),
            row.get("name_category", String.class),
            row.get("currency", String.class),
            row.get("amount", BigDecimal.class),
            row.get("spent", BigDecimal.class)))
        .all();
  }

  /**
   * Obtiene las cuotas de préstamos sin cubrir que vencen entre dos días, ambos incluidos,
   * de todos los usuarios avisables. Los préstamos cerrados no avisan.
   *
   * @param from primer día del rango
   * @param to   último día del rango
   * @return flujo con las cuotas de las que avisar
   */
  public Flux<DueLoanNotice> findLoanInstallmentsDue(LocalDate from, LocalDate to) {
    return databaseClient.sql(LOAN_INSTALLMENTS_DUE)
        .bind("from", from)
        .bind("to", to)
        .map(row -> new DueLoanNotice(
            row.get("user_id", Long.class),
            row.get("id_loan", Long.class),
            row.get("id_loan_installment", Long.class),
            row.get("borrower_name", String.class),
            row.get("currency", String.class),
            row.get("number", Integer.class),
            row.get("installment_count", Integer.class),
            row.get("due_date", LocalDate.class),
            row.get("pending", BigDecimal.class)))
        .all();
  }

  /**
   * Apunta un aviso antes de mandarlo, y dice si le toca a quien lo apunta.
   *
   * <p>Es lo que hace que la tarea pueda repetirse: si el aviso ya estaba apuntado —otra
   * pasada, otro arranque, otra instancia— la restricción de unicidad descarta la fila y aquí
   * se devuelve falso, así que nadie lo manda dos veces.</p>
   *
   * @param userId      destinatario
   * @param kind        tipo de aviso
   * @param referenceId fijo o presupuesto del que se avisa
   * @param periodKey   ocurrencia: fecha de vencimiento o mes
   * @return si el apunte entró y el aviso hay que mandarlo
   */
  public Mono<Boolean> claim(Long userId, String kind, Long referenceId, String periodKey) {
    return databaseClient.sql("""
            INSERT INTO notification_log (user_id, kind, reference_id, period_key)
            VALUES (:userId, :kind, :referenceId, :periodKey)
            ON CONFLICT DO NOTHING
            """)
        .bind("userId", userId)
        .bind("kind", kind)
        .bind("referenceId", referenceId)
        .bind("periodKey", periodKey)
        .fetch()
        .rowsUpdated()
        .map(rows -> rows > 0);
  }

  /**
   * Retira un apunte para que la siguiente pasada vuelva a intentar el aviso.
   * Solo se usa cuando ningún dispositivo lo recibió por un fallo pasajero del servicio de
   * push: dejarlo apuntado sería darlo por avisado sin que llegara a ninguna parte.
   *
   * @param userId      destinatario
   * @param kind        tipo de aviso
   * @param referenceId fijo o presupuesto
   * @param periodKey   ocurrencia
   * @return número de filas borradas
   */
  public Mono<Long> release(Long userId, String kind, Long referenceId, String periodKey) {
    return databaseClient.sql("""
            DELETE FROM notification_log
            WHERE user_id = :userId AND kind = :kind
              AND reference_id = :referenceId AND period_key = :periodKey
            """)
        .bind("userId", userId)
        .bind("kind", kind)
        .bind("referenceId", referenceId)
        .bind("periodKey", periodKey)
        .fetch()
        .rowsUpdated();
  }

  /**
   * Borra los apuntes viejos. Un aviso de hace meses ya no puede repetirse —su fecha pasó—,
   * así que conservarlo solo hace crecer la tabla.
   *
   * @param before instante antes del cual se borra
   * @return número de filas borradas
   */
  public Mono<Long> purgeLogBefore(LocalDateTime before) {
    return databaseClient.sql("DELETE FROM notification_log WHERE sent_at < :before")
        .bind("before", before)
        .fetch()
        .rowsUpdated();
  }

  private static NotificationSettings toSettings(Readable row) {
    return new NotificationSettings(
        Boolean.TRUE.equals(row.get("recurring_due", Boolean.class)),
        Boolean.TRUE.equals(row.get("budget_limit", Boolean.class)),
        Boolean.TRUE.equals(row.get("loan_due", Boolean.class)));
  }
}
