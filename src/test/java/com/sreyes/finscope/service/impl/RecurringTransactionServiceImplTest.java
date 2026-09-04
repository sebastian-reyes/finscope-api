package com.sreyes.finscope.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.api.model.ConfirmRecurringTransactionRequest;
import com.sreyes.finscope.api.model.SaveRecurringTransactionRequest;
import com.sreyes.finscope.api.model.UpdateRecurringTransactionRequest;
import com.sreyes.finscope.exception.custom.CategoryNotApplicableException;
import com.sreyes.finscope.exception.custom.RecurringAlreadyConfirmedException;
import com.sreyes.finscope.exception.custom.RecurringDateOutOfPeriodException;
import com.sreyes.finscope.exception.custom.RecurringNotDueException;
import com.sreyes.finscope.exception.custom.RecurringNotFoundException;
import com.sreyes.finscope.exception.custom.RecurringSkippedException;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.entity.RecurringTransaction;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.model.query.RecurringDetail;
import com.sreyes.finscope.model.query.RecurringState;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.repository.RecurringSkipRepository;
import com.sreyes.finscope.repository.RecurringTransactionRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 * Pruebas unitarias de {@link RecurringTransactionServiceImpl}, centradas en lo que puede
 * fallar sin que se note: que el estado del mes se decida en el orden equivocado, que
 * confirmar registre el movimiento fuera del mes que dice estar confirmando, y que un mes
 * ya resuelto se pueda volver a confirmar y acabe contando el mismo cargo dos veces.
 *
 * El reloj está fijado a propósito. La única diferencia entre un pendiente y un vencido es
 * qué día es hoy, así que con el reloj del sistema estas pruebas empezarían a decir cosas
 * distintas según la fecha en que se ejecutaran.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecurringTransactionServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final Long RECURRING_ID = 11L;
  private static final Long CATEGORY_ID = 4L;
  private static final Long TYPE_ID = 2L;
  private static final Integer MONTH = 8;
  private static final Integer YEAR = 2026;

  /** 20 de agosto de 2026: dentro del mes que miran las pruebas y ya pasado el día 12. */
  private static final Clock CLOCK = Clock.fixed(
      LocalDateTime.of(2026, 8, 20, 10, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

  @Mock
  private RecurringTransactionRepository recurringRepository;

  @Mock
  private RecurringSkipRepository recurringSkipRepository;

  @Mock
  private TransactionRepository transactionRepository;

  @Mock
  private TransactionTypeRepository transactionTypeRepository;

  @Mock
  private CategoryRepository categoryRepository;

  private RecurringTransactionServiceImpl recurringService;

  @BeforeEach
  void setUp() {
    recurringService = new RecurringTransactionServiceImpl(recurringRepository,
        recurringSkipRepository, transactionRepository, transactionTypeRepository,
        categoryRepository, CLOCK);
  }

  /** Categoría de egresos, la que clasifica el internet. */
  private Category servicios() {
    return new Category(CATEGORY_ID, USER_ID, "Servicios", "EXPENSE", false);
  }

  /** Categoría de solo ingresos: no admite un egreso. */
  private Category salario() {
    return new Category(20L, USER_ID, "Salario", "INCOME", false);
  }

  private TransactionType expense() {
    return new TransactionType(TYPE_ID, "Egreso", "EXPENSE");
  }

  private RecurringTransaction recurring() {
    return new RecurringTransaction(RECURRING_ID, USER_ID, CATEGORY_ID, TYPE_ID, "Internet",
        new BigDecimal("180.00"), 12, 1, 1, 2026, true);
  }

  /**
   * Construye la fila que devolvería la consulta para un mes.
   *
   * @param day           día previsto, sin recortar
   * @param due           si la plantilla vence en ese mes
   * @param skipped       si el usuario omitió el mes
   * @param transactionId movimiento con el que se confirmó, nulo si sigue sin confirmar
   * @return la proyección tal y como llegaría de la base
   */
  private RecurringDetail detail(int day, boolean due, boolean skipped, Long transactionId) {
    return new RecurringDetail(RECURRING_ID, CATEGORY_ID, "Servicios", TYPE_ID, "EXPENSE",
        "Internet", new BigDecimal("180.00"), day, 1, 1, 2026, true, MONTH, YEAR, due, skipped,
        transactionId,
        transactionId == null ? null : new BigDecimal("175.00"),
        transactionId == null ? null : LocalDateTime.of(2026, 8, 12, 9, 0));
  }

  /** Lo que la consulta devuelve cada vez que se le pregunta por el mes de las pruebas. */
  private void givenDetail(RecurringDetail only) {
    when(recurringRepository.findDetailById(eq(USER_ID), eq(RECURRING_ID), eq(MONTH), eq(YEAR),
        any(), any())).thenReturn(Mono.just(only));
  }

  /**
   * Lo que devuelve la consulta antes y después de escribir.
   * Las operaciones que cambian algo releen el detalle para responder con la misma forma
   * que devolvería listar el mes, así que la segunda lectura ya ve el cambio.
   */
  private void givenDetail(RecurringDetail before, RecurringDetail after) {
    when(recurringRepository.findDetailById(eq(USER_ID), eq(RECURRING_ID), eq(MONTH), eq(YEAR),
        any(), any())).thenReturn(Mono.just(before), Mono.just(after));
  }

  @Test
  @DisplayName("Un fijo que vence y sigue sin pagar después de su día sale como vencido")
  void marksOverdueAfterTheDueDay() {
    when(recurringRepository.findDetailsByPeriod(eq(USER_ID), eq(MONTH), eq(YEAR), any(), any()))
        .thenReturn(Flux.just(detail(12, true, false, null)));

    StepVerifier.create(recurringService.findRecurring(USER_ID, MONTH, YEAR))
        .assertNext(occurrence -> {
          assertEquals(RecurringState.OVERDUE, occurrence.state());
          assertEquals(LocalDate.of(2026, 8, 12), occurrence.dueDate());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("El mismo fijo sale pendiente si su día todavía no ha llegado")
  void marksPendingBeforeTheDueDay() {
    when(recurringRepository.findDetailsByPeriod(eq(USER_ID), eq(MONTH), eq(YEAR), any(), any()))
        .thenReturn(Flux.just(detail(28, true, false, null)));

    StepVerifier.create(recurringService.findRecurring(USER_ID, MONTH, YEAR))
        .assertNext(occurrence -> assertEquals(RecurringState.PENDING, occurrence.state()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Un movimiento enlazado manda sobre la plantilla que después dejó de vencer")
  void paidWinsOverNotDue() {
    when(recurringRepository.findDetailsByPeriod(eq(USER_ID), eq(MONTH), eq(YEAR), any(), any()))
        .thenReturn(Flux.just(detail(12, false, false, 99L)));

    StepVerifier.create(recurringService.findRecurring(USER_ID, MONTH, YEAR))
        // Pausar el gimnasio en octubre no puede borrar que en agosto se pagó.
        .assertNext(occurrence -> assertEquals(RecurringState.PAID, occurrence.state()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Una plantilla que no vence tapa la omisión que tuviera ese mes")
  void notDueWinsOverSkipped() {
    when(recurringRepository.findDetailsByPeriod(eq(USER_ID), eq(MONTH), eq(YEAR), any(), any()))
        .thenReturn(Flux.just(detail(12, false, true, null)));

    StepVerifier.create(recurringService.findRecurring(USER_ID, MONTH, YEAR))
        .assertNext(occurrence -> {
          assertEquals(RecurringState.NOT_DUE, occurrence.state());
          // Sin vencimiento no hay día que enseñar.
          assertNull(occurrence.dueDate());
        })
        .verifyComplete();
  }

  @Test
  @DisplayName("El día previsto se recorta al último del mes en los meses cortos")
  void clampsTheDueDayToTheLengthOfTheMonth() {
    RecurringDetail february = new RecurringDetail(RECURRING_ID, CATEGORY_ID, "Servicios",
        TYPE_ID, "EXPENSE", "Internet", new BigDecimal("180.00"), 31, 1, 1, 2026, true, 2, 2026,
        true, false, null, null, null);
    when(recurringRepository.findDetailsByPeriod(eq(USER_ID), eq(2), eq(2026), any(), any()))
        .thenReturn(Flux.just(february));

    StepVerifier.create(recurringService.findRecurring(USER_ID, 2, 2026))
        .assertNext(occurrence -> assertEquals(LocalDate.of(2026, 2, 28), occurrence.dueDate()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Confirmar sin datos usa el importe de la plantilla y la deja enlazada")
  void confirmsWithTheTemplateAmount() {
    givenDetail(detail(12, true, false, null), detail(12, true, false, 99L));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(call -> Mono.just(call.getArgument(0)));

    StepVerifier.create(recurringService.confirmRecurring(USER_ID, RECURRING_ID,
        new ConfirmRecurringTransactionRequest(MONTH, YEAR)))
        .assertNext(occurrence -> assertEquals(RecurringState.PAID, occurrence.state()))
        .verifyComplete();

    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(saved.capture());
    assertEquals(new BigDecimal("180.00"), saved.getValue().getAmount());
    assertEquals("Internet", saved.getValue().getDescription());
    assertEquals(CATEGORY_ID, saved.getValue().getCategoryId());
    assertEquals(TYPE_ID, saved.getValue().getTransactionTypeId());
    // El enlace es lo único que permite saber después que ese mes ya está pagado.
    assertEquals(RECURRING_ID, saved.getValue().getRecurringId());
    // Hoy cae dentro del mes, así que se registra hoy y no el día previsto.
    assertEquals(LocalDate.of(2026, 8, 20), saved.getValue().getDate().toLocalDate());
  }

  @Test
  @DisplayName("Confirmar un mes ya pasado fecha el movimiento en su día previsto")
  void confirmsAPastMonthOnItsDueDay() {
    RecurringDetail july = new RecurringDetail(RECURRING_ID, CATEGORY_ID, "Servicios", TYPE_ID,
        "EXPENSE", "Internet", new BigDecimal("180.00"), 12, 1, 1, 2026, true, 7, 2026, true,
        false, null, null, null);
    when(recurringRepository.findDetailById(eq(USER_ID), eq(RECURRING_ID), eq(7), eq(2026),
        any(), any())).thenReturn(Mono.just(july));
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(call -> Mono.just(call.getArgument(0)));

    StepVerifier.create(recurringService.confirmRecurring(USER_ID, RECURRING_ID,
        new ConfirmRecurringTransactionRequest(7, 2026)))
        .expectNextCount(1)
        .verifyComplete();

    ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(saved.capture());
    // Usar hoy sacaría de julio al recibo de julio, y el mes dejaría de cuadrar.
    assertEquals(LocalDate.of(2026, 7, 12), saved.getValue().getDate().toLocalDate());
  }

  @Test
  @DisplayName("Confirmar con una fecha fuera del mes se rechaza")
  void refusesADateOutsideTheConfirmedMonth() {
    givenDetail(detail(12, true, false, null));
    ConfirmRecurringTransactionRequest request =
        new ConfirmRecurringTransactionRequest(MONTH, YEAR);
    request.setDate(LocalDateTime.of(2026, 9, 2, 10, 0));

    StepVerifier.create(recurringService.confirmRecurring(USER_ID, RECURRING_ID, request))
        .expectError(RecurringDateOutOfPeriodException.class)
        .verify();

    // Fechado fuera, el mes seguiría pendiente y admitiría una segunda confirmación.
    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  @DisplayName("No se puede confirmar dos veces el mismo mes")
  void refusesToConfirmTwice() {
    givenDetail(detail(12, true, false, 99L));

    StepVerifier.create(recurringService.confirmRecurring(USER_ID, RECURRING_ID,
        new ConfirmRecurringTransactionRequest(MONTH, YEAR)))
        .expectError(RecurringAlreadyConfirmedException.class)
        .verify();

    verify(transactionRepository, never()).save(any(Transaction.class));
  }

  @Test
  @DisplayName("No se puede confirmar un mes omitido sin deshacer antes la omisión")
  void refusesToConfirmASkippedMonth() {
    givenDetail(detail(12, true, true, null));

    StepVerifier.create(recurringService.confirmRecurring(USER_ID, RECURRING_ID,
        new ConfirmRecurringTransactionRequest(MONTH, YEAR)))
        .expectError(RecurringSkippedException.class)
        .verify();
  }

  @Test
  @DisplayName("No se puede confirmar un mes en el que el fijo no vence")
  void refusesToConfirmAMonthItIsNotDue() {
    givenDetail(detail(12, false, false, null));

    StepVerifier.create(recurringService.confirmRecurring(USER_ID, RECURRING_ID,
        new ConfirmRecurringTransactionRequest(MONTH, YEAR)))
        .expectError(RecurringNotDueException.class)
        .verify();
  }

  @Test
  @DisplayName("Omitir un mes ya confirmado se rechaza")
  void refusesToSkipAConfirmedMonth() {
    givenDetail(detail(12, true, false, 99L));

    StepVerifier.create(recurringService.skipRecurring(USER_ID, RECURRING_ID, MONTH, YEAR))
        .expectError(RecurringAlreadyConfirmedException.class)
        .verify();

    verify(recurringSkipRepository, never()).insertIfAbsent(anyLong(), any(), any());
  }

  @Test
  @DisplayName("Deshacer una omisión que no existía no falla")
  void unskippingIsAlwaysSafe() {
    givenDetail(detail(12, true, false, null));
    when(recurringSkipRepository.deleteByPeriod(RECURRING_ID, MONTH, YEAR))
        .thenReturn(Mono.just(0L));

    StepVerifier.create(recurringService.unskipRecurring(USER_ID, RECURRING_ID, MONTH, YEAR))
        .assertNext(occurrence -> assertEquals(RecurringState.OVERDUE, occurrence.state()))
        .verifyComplete();
  }

  @Test
  @DisplayName("Una plantilla de otra cuenta se comporta como una inexistente")
  void hidesRecurringOfOtherUsers() {
    when(recurringRepository.findDetailById(eq(USER_ID), eq(RECURRING_ID), eq(MONTH), eq(YEAR),
        any(), any())).thenReturn(Mono.empty());

    StepVerifier.create(recurringService.skipRecurring(USER_ID, RECURRING_ID, MONTH, YEAR))
        .expectError(RecurringNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("No se da de alta un fijo con una categoría que no admite su tipo")
  void refusesACategoryThatDoesNotTakeThatType() {
    when(transactionTypeRepository.findById(TYPE_ID)).thenReturn(Mono.just(expense()));
    when(categoryRepository.findByIdAndUserId(20L, USER_ID)).thenReturn(Mono.just(salario()));

    SaveRecurringTransactionRequest request = new SaveRecurringTransactionRequest(20L, TYPE_ID,
        "Internet", new BigDecimal("180.00"), 12, 8, 2026);

    StepVerifier.create(recurringService.createRecurring(USER_ID, request))
        .expectError(CategoryNotApplicableException.class)
        .verify();

    verify(recurringRepository, never()).save(any(RecurringTransaction.class));
  }

  @Test
  @DisplayName("Cambiar solo el tipo revalida la categoría que la plantilla ya tenía")
  void revalidatesTheCategoryWhenOnlyTheTypeChanges() {
    when(recurringRepository.findByIdAndUserId(RECURRING_ID, USER_ID))
        .thenReturn(Mono.just(recurring()));
    when(transactionTypeRepository.findById(1L))
        .thenReturn(Mono.just(new TransactionType(1L, "Ingreso", "INCOME")));
    when(categoryRepository.findByIdAndUserId(CATEGORY_ID, USER_ID))
        .thenReturn(Mono.just(servicios()));

    UpdateRecurringTransactionRequest request = new UpdateRecurringTransactionRequest();
    request.setTransactionTypeId(1L);

    // Servicios solo admite egresos, así que pasarlo a ingreso sin tocar la categoría deja
    // una pareja que el registro rechazaría el día que tocara confirmarlo.
    StepVerifier.create(recurringService.updateRecurring(USER_ID, RECURRING_ID, request))
        .expectError(CategoryNotApplicableException.class)
        .verify();

    verify(recurringRepository, never()).save(any(RecurringTransaction.class));
  }

  @Test
  @DisplayName("Pausar un fijo no toca ningún movimiento")
  void pausingOnlyTouchesTheTemplate() {
    when(recurringRepository.findByIdAndUserId(RECURRING_ID, USER_ID))
        .thenReturn(Mono.just(recurring()));
    when(recurringRepository.save(any(RecurringTransaction.class)))
        .thenAnswer(call -> Mono.just(call.getArgument(0)));

    UpdateRecurringTransactionRequest request = new UpdateRecurringTransactionRequest();
    request.setActive(false);

    StepVerifier.create(recurringService.updateRecurring(USER_ID, RECURRING_ID, request))
        .assertNext(updated -> assertEquals(false, updated.getActive()))
        .verifyComplete();

    verify(transactionRepository, never()).save(any(Transaction.class));
  }
}
