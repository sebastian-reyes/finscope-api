package com.sreyes.finscope.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.util.query.DateRanges;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base de las pruebas de repositorio contra un PostgreSQL de verdad.
 *
 * <p>El resto de pruebas del proyecto simulan el repositorio, así que el SQL escrito a mano
 * no lo ejecutaba nada antes de producción. Y es justo donde están los fallos que no avisan:
 * una proyección cuyo componente se llama como una propiedad de la entidad sale entera a
 * nulo sin ningún error, una unión mal puesta duplica importes y una condición de fechas
 * desplazada deja fuera el último día del mes. Compila, los servicios pasan sus pruebas y el
 * síntoma aparece en el navegador como una lista vacía o una cifra que no cuadra.</p>
 *
 * <p>La base es un contenedor efímero con la misma versión mayor que la de producción, y el
 * esquema lo crean las migraciones de Flyway, las mismas que se aplican al desplegar. Así,
 * además de las consultas, cada ejecución comprueba que las migraciones corren de cero.</p>
 *
 * <p>El contenedor se arranca una sola vez y lo comparten todas las clases, igual que el
 * contexto de Spring, que es idéntico en todas. Ninguna prueba borra datos: cada una crea su
 * propio usuario, y como todas las consultas acotan por propietario, lo de una no se ve
 * desde otra.</p>
 *
 * <p>Corre con un perfil propio y no con {@code dev}, que es el perfil por defecto de la
 * aplicación: ese apunta a un PostgreSQL en {@code localhost} y, si la conexión al
 * contenedor fallara al configurarse, las pruebas acabarían escribiendo en la base local de
 * quien las lanza. {@link #runsAgainstTheContainer()} lo comprueba antes de cada prueba.</p>
 */
@DataR2dbcTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@ActiveProfiles("repository-test")
abstract class PostgresRepositoryTest {

  /** Nombre de la base del contenedor; ninguna base local se llama así. */
  private static final String DATABASE = "finscope_repository_test";

  /** Identificadores fijos del catálogo de tipos, sembrados por la migración inicial. */
  static final long INCOME = 1L;
  static final long EXPENSE = 2L;

  @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:16-alpine").withDatabaseName(DATABASE);

  static {
    POSTGRES.start();
  }

  @Autowired
  protected DatabaseClient db;

  /**
   * Garantiza que las consultas van a la base del contenedor y que en ella corrieron las
   * migraciones. Si alguna de las dos cosas falla, la prueba no debe seguir: o escribiría
   * donde no debe o probaría contra un esquema que no es el de la aplicación.
   */
  @BeforeEach
  void runsAgainstTheContainer() {
    String database = db.sql("SELECT current_database() AS name")
        .map(row -> row.get("name", String.class)).one().block();
    Long migrations = db.sql("SELECT COUNT(*) AS applied FROM flyway_schema_history WHERE success")
        .map(row -> row.get("applied", Long.class)).one().block();

    assertThat(database).isEqualTo(DATABASE);
    assertThat(migrations).isPositive();
  }

  /**
   * Crea un usuario nuevo con un correo que no se repite entre pruebas.
   *
   * @return identificador del usuario
   */
  protected long user() {
    return insert("INSERT INTO users (email) VALUES (:email) RETURNING id_user",
        "email", UUID.randomUUID() + "@repository.test");
  }

  /**
   * Crea una categoría de gasto del usuario.
   *
   * @param userId propietario
   * @param name   nombre de la categoría
   * @return identificador de la categoría
   */
  protected long category(long userId, String name) {
    return id(db.sql("""
            INSERT INTO categories (user_id, name_category, applies_to)
            VALUES (:userId, :name, 'BOTH') RETURNING id_category""")
        .bind("userId", userId).bind("name", name));
  }

  /**
   * Crea un tag del usuario.
   *
   * @param userId propietario
   * @param name   nombre del tag
   * @return identificador del tag
   */
  protected long tag(long userId, String name) {
    return id(db.sql("INSERT INTO tags (user_id, name_tag) VALUES (:userId, :name) RETURNING id_tag")
        .bind("userId", userId).bind("name", name));
  }

  /**
   * Registra un movimiento en soles.
   *
   * @param userId     propietario
   * @param categoryId categoría
   * @param typeId     {@link #INCOME} o {@link #EXPENSE}
   * @param amount     importe
   * @param date       fecha y hora del movimiento
   * @return identificador del movimiento
   */
  protected long transaction(long userId, long categoryId, long typeId, String amount,
                             LocalDateTime date) {
    return transaction(userId, categoryId, typeId, amount, "PEN", null, date, null);
  }

  /**
   * Registra un movimiento con todos sus datos.
   *
   * @param userId       propietario
   * @param categoryId   categoría
   * @param typeId       {@link #INCOME} o {@link #EXPENSE}
   * @param amount       importe en su moneda
   * @param currency     moneda del movimiento
   * @param exchangeRate tipo de cambio a la base, nulo en soles
   * @param date         fecha y hora del movimiento
   * @param recurringId  fijo que confirmó, o nulo
   * @return identificador del movimiento
   */
  protected long transaction(long userId, long categoryId, long typeId, String amount,
                             String currency, String exchangeRate, LocalDateTime date,
                             Long recurringId) {
    DatabaseClient.GenericExecuteSpec spec = db.sql("""
            INSERT INTO transactions (amount, currency, exchange_rate, date, transaction_type_id,
                                      user_id, category_id, recurring_id)
            VALUES (:amount, :currency, :exchangeRate, :date, :typeId, :userId, :categoryId,
                    :recurringId)
            RETURNING id_transaction""")
        .bind("amount", new BigDecimal(amount))
        .bind("currency", currency)
        .bind("date", date)
        .bind("typeId", typeId)
        .bind("userId", userId)
        .bind("categoryId", categoryId);
    spec = exchangeRate == null
        ? spec.bindNull("exchangeRate", BigDecimal.class)
        : spec.bind("exchangeRate", new BigDecimal(exchangeRate));
    spec = recurringId == null
        ? spec.bindNull("recurringId", Long.class)
        : spec.bind("recurringId", recurringId);
    return id(spec);
  }

  /**
   * Pone un tag a un movimiento.
   *
   * @param transactionId movimiento
   * @param tagId         tag
   */
  protected void tagTransaction(long transactionId, long tagId) {
    db.sql("INSERT INTO transaction_tags (transaction_id, tag_id) VALUES (:transactionId, :tagId)")
        .bind("transactionId", transactionId).bind("tagId", tagId)
        .then().block();
  }

  /**
   * Fija un presupuesto en soles.
   *
   * @param userId     propietario
   * @param categoryId categoría presupuestada
   * @param month      mes
   * @param year       año
   * @param amount     importe presupuestado
   * @return identificador del presupuesto
   */
  protected long budget(long userId, long categoryId, int month, int year, String amount) {
    return id(db.sql("""
            INSERT INTO budgets (user_id, category_id, month, year, currency, amount)
            VALUES (:userId, :categoryId, :month, :year, 'PEN', :amount)
            RETURNING id_budget""")
        .bind("userId", userId).bind("categoryId", categoryId)
        .bind("month", month).bind("year", year).bind("amount", new BigDecimal(amount)));
  }

  /**
   * Crea un movimiento fijo de gasto en soles.
   *
   * @param userId      propietario
   * @param categoryId  categoría
   * @param amount      importe de cada mes
   * @param dayOfMonth  día en que vence
   * @param everyMonths cada cuántos meses vence
   * @param startMonth  mes de arranque
   * @param startYear   año de arranque
   * @return identificador del fijo
   */
  protected long recurring(long userId, long categoryId, String amount, int dayOfMonth,
                           int everyMonths, int startMonth, int startYear) {
    return id(db.sql("""
            INSERT INTO recurring_transactions (user_id, category_id, transaction_type_id,
                                                description, amount, currency, day_of_month,
                                                every_months, start_month, start_year)
            VALUES (:userId, :categoryId, :typeId, 'Internet', :amount, 'PEN', :day, :every,
                    :startMonth, :startYear)
            RETURNING id_recurring""")
        .bind("userId", userId).bind("categoryId", categoryId).bind("typeId", EXPENSE)
        .bind("amount", new BigDecimal(amount)).bind("day", dayOfMonth).bind("every", everyMonths)
        .bind("startMonth", startMonth).bind("startYear", startYear));
  }

  /**
   * Omite un mes de un fijo.
   *
   * @param recurringId fijo
   * @param month       mes omitido
   * @param year        año omitido
   */
  protected void skip(long recurringId, int month, int year) {
    db.sql("INSERT INTO recurring_skips (recurring_id, month, year) VALUES (:id, :month, :year)")
        .bind("id", recurringId).bind("month", month).bind("year", year)
        .then().block();
  }

  /**
   * Rango de un mes natural tal y como lo resuelven los servicios, para que las pruebas
   * acoten exactamente igual que la aplicación.
   *
   * @param month mes
   * @param year  año
   * @return el rango, inclusivo por los dos extremos
   */
  protected static DateRange period(int month, int year) {
    return DateRanges.resolve(month, year, null, null);
  }

  private long insert(String sql, String name, Object value) {
    return id(db.sql(sql).bind(name, value));
  }

  private static long id(DatabaseClient.GenericExecuteSpec spec) {
    return spec.map(row -> ((Number) row.get(0)).longValue()).one().block();
  }
}
