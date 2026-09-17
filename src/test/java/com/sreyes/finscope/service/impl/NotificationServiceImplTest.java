package com.sreyes.finscope.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.model.query.BudgetLimitNotice;
import com.sreyes.finscope.model.query.DueRecurringNotice;
import com.sreyes.finscope.model.query.PushMessage;
import com.sreyes.finscope.repository.NotificationRepository;
import com.sreyes.finscope.security.PushProperties;
import com.sreyes.finscope.service.PushService;
import com.sreyes.finscope.service.PushService.Delivery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas de {@link NotificationServiceImpl}.
 *
 * Lo que fijan es lo que haría que un aviso molestara en vez de ayudar: que saliera de
 * madrugada, que saliera una noche antes por culpa de la zona horaria del servidor, que se
 * repitiera, o que se diera por mandado sin haber llegado a ninguna parte.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final PushProperties PROPERTIES =
      new PushProperties(null, null, null, "America/Lima", 8, 21, 0.9, null);

  @Mock
  private NotificationRepository repository;

  @Mock
  private PushService pushService;

  @BeforeEach
  void setUp() {
    when(pushService.enabled()).thenReturn(true);
    when(repository.findRecurringDueOn(any(), any(), any())).thenReturn(Flux.empty());
    when(repository.findBudgetsReaching(anyInt(), anyInt(), any(), any(), anyDouble()))
        .thenReturn(Flux.empty());
    when(repository.purgeLogBefore(any())).thenReturn(Mono.just(0L));
    when(repository.claim(anyLong(), anyString(), anyLong(), anyString()))
        .thenReturn(Mono.just(true));
    when(repository.release(anyLong(), anyString(), anyLong(), anyString()))
        .thenReturn(Mono.just(1L));
    when(pushService.notifyUser(anyLong(), any())).thenReturn(Mono.just(new Delivery(1, 0)));
  }

  /** El servicio con el reloj parado en un instante UTC. */
  private NotificationServiceImpl at(String instant) {
    return new NotificationServiceImpl(repository, pushService, PROPERTIES,
        Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
  }

  private static DueRecurringNotice rent() {
    return new DueRecurringNotice(USER_ID, 12L, "Alquiler", new BigDecimal("1200.00"), "PEN",
        "EXPENSE");
  }

  @Test
  @DisplayName("A las 20:30 de Lima, que en UTC ya es el día siguiente, «mañana» sigue siendo "
      + "el 16")
  void decidesTheDayInTheUsersZone() {
    // 01:30 UTC del 16 son las 20:30 del 15 en Lima.
    StepVerifier.create(at("2026-09-16T01:30:00Z").runScheduled()).verifyComplete();

    verify(repository).findRecurringDueOn(eq(LocalDate.of(2026, 9, 16)), any(), any());
    verify(repository).findRecurringDueOn(eq(LocalDate.of(2026, 9, 15)), any(), any());
  }

  @Test
  @DisplayName("Fuera de la ventana horaria no consulta ni manda nada")
  void staysQuietAtNight() {
    // 08:00 UTC son las 03:00 en Lima.
    StepVerifier.create(at("2026-09-15T08:00:00Z").runScheduled()).verifyComplete();

    verify(repository, never()).findRecurringDueOn(any(), any(), any());
    verify(pushService, never()).notifyUser(anyLong(), any());
  }

  @Test
  @DisplayName("Sin claves VAPID la tarea no hace nada")
  void doesNothingWhenDisabled() {
    when(pushService.enabled()).thenReturn(false);

    StepVerifier.create(at("2026-09-15T15:00:00Z").runScheduled()).verifyComplete();

    verify(repository, never()).findRecurringDueOn(any(), any(), any());
  }

  @Test
  @DisplayName("Avisa de un fijo que vence mañana y lo apunta con su fecha")
  void notifiesRecurringDueTomorrow() {
    LocalDate tomorrow = LocalDate.of(2026, 9, 16);
    when(repository.findRecurringDueOn(eq(tomorrow), any(), any())).thenReturn(Flux.just(rent()));

    StepVerifier.create(at("2026-09-15T15:00:00Z").runScheduled()).verifyComplete();

    verify(repository).claim(USER_ID, "RECURRING_DUE_TOMORROW", 12L, "2026-09-16");
    ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
    verify(pushService).notifyUser(eq(USER_ID), message.capture());
    assertThat(message.getValue().title()).isEqualTo("Mañana vence: Alquiler");
    assertThat(message.getValue().body()).startsWith("S/ 1,200.00");
    assertThat(message.getValue().url()).isEqualTo("/recurring?mes=2026-09");
  }

  @Test
  @DisplayName("Lo que ya estaba apuntado no se vuelve a mandar")
  void doesNotRepeatClaimedNotifications() {
    when(repository.findRecurringDueOn(any(), any(), any())).thenReturn(Flux.just(rent()));
    when(repository.claim(anyLong(), anyString(), anyLong(), anyString()))
        .thenReturn(Mono.just(false));

    StepVerifier.create(at("2026-09-15T15:00:00Z").runScheduled()).verifyComplete();

    verify(pushService, never()).notifyUser(anyLong(), any());
  }

  @Test
  @DisplayName("Si ningún dispositivo lo recibe por un fallo pasajero, se retira el apunte")
  void releasesWhenNothingWasDelivered() {
    LocalDate tomorrow = LocalDate.of(2026, 9, 16);
    when(repository.findRecurringDueOn(eq(tomorrow), any(), any())).thenReturn(Flux.just(rent()));
    when(pushService.notifyUser(anyLong(), any())).thenReturn(Mono.just(new Delivery(0, 1)));

    StepVerifier.create(at("2026-09-15T15:00:00Z").runScheduled()).verifyComplete();

    verify(repository).release(USER_ID, "RECURRING_DUE_TOMORROW", 12L, "2026-09-16");
  }

  @Test
  @DisplayName("Un presupuesto que salta por encima del límite avisa solo de que se pasó")
  void overBudgetSkipsTheNearWarning() {
    BudgetLimitNotice food = new BudgetLimitNotice(USER_ID, 30L, "Comida", "PEN",
        new BigDecimal("400.00"), new BigDecimal("430.00"));
    when(repository.findBudgetsReaching(eq(9), eq(2026), any(), any(), eq(0.9)))
        .thenReturn(Flux.just(food));

    StepVerifier.create(at("2026-09-15T15:00:00Z").runScheduled()).verifyComplete();

    verify(repository).claim(USER_ID, "BUDGET_NEAR_LIMIT", 30L, "2026-09");
    verify(repository).claim(USER_ID, "BUDGET_OVER_LIMIT", 30L, "2026-09");
    ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
    verify(pushService).notifyUser(eq(USER_ID), message.capture());
    assertThat(message.getValue().title()).isEqualTo("Comida: te pasaste del presupuesto");
  }

  @Test
  @DisplayName("Los textos distinguen pago de cobro y redondean el porcentaje hacia abajo")
  void composesMessages() {
    DueRecurringNotice salary = new DueRecurringNotice(USER_ID, 3L, "Sueldo",
        new BigDecimal("3500"), "USD", "INCOME");
    PushMessage today = NotificationServiceImpl.recurringMessage(salary, "RECURRING_DUE_TODAY",
        LocalDate.of(2026, 9, 30));
    assertThat(today.title()).isEqualTo("Hoy toca cobrar: Sueldo");
    assertThat(today.body()).startsWith("$ 3,500.00");

    BudgetLimitNotice near = new BudgetLimitNotice(USER_ID, 30L, "Comida", "PEN",
        new BigDecimal("400.00"), new BigDecimal("399.90"));
    PushMessage message = NotificationServiceImpl.budgetMessage(near, false,
        YearMonth.of(2026, 9));
    assertThat(message.title()).isEqualTo("Comida: llevas el 99 % del presupuesto");
    assertThat(message.body()).isEqualTo("S/ 399.90 de S/ 400.00 en setiembre.");
  }
}
