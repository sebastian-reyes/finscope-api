package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.model.query.BudgetLimitNotice;
import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.model.query.DueRecurringNotice;
import com.sreyes.finscope.model.query.PushMessage;
import com.sreyes.finscope.repository.NotificationRepository;
import com.sreyes.finscope.security.PushProperties;
import com.sreyes.finscope.service.NotificationService;
import com.sreyes.finscope.service.PushService;
import com.sreyes.finscope.util.query.DateRanges;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link NotificationService}.
 *
 * <p><strong>El día se decide en la zona de los usuarios, no en la del servidor.</strong> El
 * contenedor corre en UTC, cinco horas por delante de Lima: con el reloj del servidor, a las
 * 20:00 de Lima ya sería «mañana» y el aviso del día siguiente saldría una noche antes. Por eso
 * el reloj se lleva a la zona configurada antes de preguntar la fecha o la hora.</p>
 *
 * <p>Cada aviso se apunta antes de mandarse ({@link NotificationRepository#claim}). Si el apunte
 * ya existía, otra pasada lo mandó y este no sale. Si se apunta pero ningún dispositivo lo recibe
 * por un fallo pasajero, el apunte se retira para que la pasada siguiente lo intente otra vez.
 * Si algún dispositivo lo recibió, se queda: repetirlo en los demás duplicaría el aviso en el
 * que sí llegó.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

  static final String RECURRING_DUE_TOMORROW = "RECURRING_DUE_TOMORROW";
  static final String RECURRING_DUE_TODAY = "RECURRING_DUE_TODAY";
  static final String BUDGET_NEAR_LIMIT = "BUDGET_NEAR_LIMIT";
  static final String BUDGET_OVER_LIMIT = "BUDGET_OVER_LIMIT";

  /** Cuánto se conserva el registro de avisos enviados. */
  private static final int LOG_RETENTION_DAYS = 90;

  private static final Locale SPANISH = Locale.forLanguageTag("es-PE");

  private final NotificationRepository notificationRepository;
  private final PushService pushService;
  private final PushProperties properties;
  private final Clock clock;

  @Override
  public Mono<Void> runScheduled() {
    return Mono.defer(() -> {
      if (!pushService.enabled()) {
        return Mono.empty();
      }
      ZonedDateTime now = ZonedDateTime.now(clock.withZone(properties.zoneId()));
      if (now.getHour() < properties.windowStartHour()
          || now.getHour() >= properties.windowEndHour()) {
        return Mono.empty();
      }
      LocalDate today = now.toLocalDate();
      return notifyRecurring(today.plusDays(1), RECURRING_DUE_TOMORROW)
          .then(notifyRecurring(today, RECURRING_DUE_TODAY))
          .then(notifyBudgets(YearMonth.from(today)))
          .then(notificationRepository
              .purgeLogBefore(LocalDateTime.now(clock).minusDays(LOG_RETENTION_DAYS)))
          .then();
    }).onErrorResume(ex -> {
      log.error("Scheduled notifications run failed", ex);
      return Mono.empty();
    });
  }

  private Mono<Void> notifyRecurring(LocalDate dueDate, String kind) {
    DateRange month = DateRanges.resolve(dueDate.getMonthValue(), dueDate.getYear(), null, null);
    return notificationRepository.findRecurringDueOn(dueDate, month.from(), month.to())
        .concatMap(notice -> deliver(notice.userId(), kind, notice.recurringId(),
            dueDate.toString(), recurringMessage(notice, kind, dueDate)))
        .then();
  }

  /**
   * Avisa de los presupuestos del mes que llegaron al umbral.
   *
   * <p>Un presupuesto que salta de golpe por encima del límite —un gasto grande de una vez—
   * solo avisa de que se pasó. El «te acercas» se apunta también, sin mandarse, porque desde
   * ese momento su condición también se cumple y la pasada siguiente lo mandaría tarde y al
   * revés: primero «te pasaste» y una hora después «te acercas».</p>
   */
  private Mono<Void> notifyBudgets(YearMonth month) {
    DateRange range = DateRanges.resolve(month.getMonthValue(), month.getYear(), null, null);
    String periodKey = month.toString();
    return notificationRepository.findBudgetsReaching(month.getMonthValue(), month.getYear(),
            range.from(), range.to(), properties.budgetNearRatio())
        .concatMap(notice -> {
          boolean over = notice.spent().compareTo(notice.amount()) >= 0;
          if (!over) {
            return deliver(notice.userId(), BUDGET_NEAR_LIMIT, notice.budgetId(), periodKey,
                budgetMessage(notice, false, month));
          }
          return notificationRepository.claim(notice.userId(), BUDGET_NEAR_LIMIT,
                  notice.budgetId(), periodKey)
              .then(deliver(notice.userId(), BUDGET_OVER_LIMIT, notice.budgetId(), periodKey,
                  budgetMessage(notice, true, month)));
        })
        .then();
  }

  private Mono<Void> deliver(Long userId, String kind, Long referenceId, String periodKey,
                             PushMessage message) {
    return notificationRepository.claim(userId, kind, referenceId, periodKey)
        .filter(Boolean::booleanValue)
        .flatMap(claimed -> pushService.notifyUser(userId, message))
        .flatMap(delivery -> delivery.delivered() == 0 && delivery.retryable() > 0
            ? notificationRepository.release(userId, kind, referenceId, periodKey).then()
            : Mono.<Void>empty())
        .onErrorResume(ex -> {
          log.warn("Could not deliver {} notification {} to user {}: {}", kind, referenceId,
              userId, ex.toString());
          return notificationRepository.release(userId, kind, referenceId, periodKey)
              .onErrorResume(ignored -> Mono.empty())
              .then();
        });
  }

  /**
   * Compone el aviso de un fijo. Un pago «vence» y un cobro «toca»: decir que el sueldo vence
   * suena a deuda.
   */
  static PushMessage recurringMessage(DueRecurringNotice notice, String kind, LocalDate dueDate) {
    boolean tomorrow = RECURRING_DUE_TOMORROW.equals(kind);
    boolean income = "INCOME".equals(notice.typeCode());
    String name = notice.description() == null || notice.description().isBlank()
        ? "un movimiento fijo"
        : notice.description().strip();
    String when = tomorrow ? "Mañana" : "Hoy";
    String title = income ? when + " toca cobrar: " + name : when + " vence: " + name;
    String amount = money(notice.amount(), notice.currency());
    String body;
    if (income) {
      body = amount + (tomorrow
          ? " · confírmalo cuando llegue."
          : " · todavía no está confirmado.");
    } else {
      body = amount + (tomorrow
          ? " · márcalo como pagado cuando lo pagues."
          : " · todavía no está marcado como pagado.");
    }
    return new PushMessage(title, body,
        "/recurring?mes=" + YearMonth.from(dueDate),
        "recurring-" + notice.recurringId() + "-" + dueDate);
  }

  /**
   * Compone el aviso de un presupuesto. El porcentaje se redondea hacia abajo: decir «100 %»
   * con 399,90 gastados de 400 anunciaría que se llegó al límite antes de tiempo.
   */
  static PushMessage budgetMessage(BudgetLimitNotice notice, boolean over, YearMonth month) {
    String category = notice.categoryName().strip();
    String title = over
        ? category + ": te pasaste del presupuesto"
        : category + ": llevas el " + percent(notice.spent(), notice.amount())
            + " % del presupuesto";
    String monthName = month.getMonth().getDisplayName(TextStyle.FULL, SPANISH);
    String body = money(notice.spent(), notice.currency()) + " de "
        + money(notice.amount(), notice.currency()) + " en " + monthName + ".";
    return new PushMessage(title, body, "/budgets?mes=" + month,
        "budget-" + notice.budgetId() + "-" + month);
  }

  private static int percent(BigDecimal spent, BigDecimal amount) {
    return spent.multiply(BigDecimal.valueOf(100))
        .divide(amount, 0, RoundingMode.DOWN)
        .intValue();
  }

  /**
   * Da formato a un importe como lo hace la aplicación web: símbolo delante, coma de miles y
   * punto decimal, que es como se escriben las cifras en Perú.
   */
  static String money(BigDecimal amount, String currency) {
    DecimalFormat format =
        new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    String symbol = "USD".equals(currency) ? "$" : "S/";
    return symbol + " " + format.format(amount);
  }
}
