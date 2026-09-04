package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.ConfirmRecurringTransactionRequest;
import com.sreyes.finscope.api.model.SaveRecurringTransactionRequest;
import com.sreyes.finscope.api.model.UpdateRecurringTransactionRequest;
import com.sreyes.finscope.exception.custom.CategoryNotApplicableException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.RecurringAlreadyConfirmedException;
import com.sreyes.finscope.exception.custom.RecurringDateOutOfPeriodException;
import com.sreyes.finscope.exception.custom.RecurringNotDueException;
import com.sreyes.finscope.exception.custom.RecurringNotFoundException;
import com.sreyes.finscope.exception.custom.RecurringSkippedException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.entity.RecurringTransaction;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.model.query.RecurringDetail;
import com.sreyes.finscope.model.query.RecurringOccurrence;
import com.sreyes.finscope.model.query.RecurringState;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.repository.RecurringSkipRepository;
import com.sreyes.finscope.repository.RecurringTransactionRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import com.sreyes.finscope.service.RecurringTransactionService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.query.DateRanges;
import com.sreyes.finscope.util.rules.CategoryRules;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link RecurringTransactionService}.
 *
 * La plantilla es el plan y las transacciones son lo que pasó. Aquí solo se escribe un
 * movimiento en un sitio, al confirmar un mes, y siempre porque el usuario lo pidió: nada
 * se genera en segundo plano.
 *
 * El estado de cada mes no se guarda. La consulta trae los hechos —si vence, si está
 * omitido, con qué movimiento se confirmó— y este servicio los combina con la fecha de hoy,
 * que es lo único que separa un pendiente de un vencido. Guardar el estado obligaría a
 * repasar todas las plantillas cada vez que se registra o se borra un movimiento, y
 * bastaría con que fallara una de esas veces para que la lista mintiera en silencio.
 *
 * El mes se traduce a un rango de fechas con {@link DateRanges}, el mismo que usan el
 * listado, los resúmenes y los presupuestos. Es lo que garantiza que el alquiler de
 * septiembre signifique lo mismo en la lista de fijos que en el gráfico del mes.
 */
@Service
@RequiredArgsConstructor
public class RecurringTransactionServiceImpl implements RecurringTransactionService {

  private final RecurringTransactionRepository recurringRepository;
  private final RecurringSkipRepository recurringSkipRepository;
  private final TransactionRepository transactionRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final CategoryRepository categoryRepository;
  private final Clock clock;

  @Override
  public Flux<RecurringOccurrence> findRecurring(Long userId, Integer month, Integer year) {
    return resolveMonth(month, year)
        .flatMapMany(range -> recurringRepository.findDetailsByPeriod(userId, month, year,
            range.from(), range.to()))
        .map(this::toOccurrence);
  }

  @Override
  public Mono<RecurringTransaction> createRecurring(Long userId,
                                                    SaveRecurringTransactionRequest request) {
    return requireUsablePair(userId, request.getCategoryId(), request.getTransactionTypeId())
        .then(Mono.defer(() -> recurringRepository.save(toEntity(userId, request))));
  }

  @Override
  public Mono<RecurringTransaction> updateRecurring(Long userId, Long id,
                                                    UpdateRecurringTransactionRequest request) {
    return requireRecurring(userId, id)
        .flatMap(recurring -> requireValidReferences(userId, recurring, request)
            .thenReturn(applyChanges(recurring, request)))
        .flatMap(recurringRepository::save);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Las omisiones se van con la plantilla por la clave foránea en cascada. Los
   * movimientos no: solo pierden el enlace, porque ocurrieron igual.</p>
   */
  @Override
  public Mono<Void> deleteRecurring(Long userId, Long id) {
    return requireRecurring(userId, id).flatMap(recurringRepository::delete);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Se vuelve a leer el detalle después de escribir, en lugar de componer la respuesta
   * con lo que se acaba de guardar, para que confirmar devuelva exactamente la misma forma
   * que devolvería listar ese mes justo después.</p>
   */
  @Override
  public Mono<RecurringOccurrence> confirmRecurring(Long userId, Long id,
                                                    ConfirmRecurringTransactionRequest request) {
    Integer month = request.getMonth();
    Integer year = request.getYear();
    return resolveMonth(month, year)
        .flatMap(range -> requireDetail(userId, id, month, year, range)
            .flatMap(detail -> requireConfirmable(detail)
                .then(Mono.defer(() -> transactionRepository.save(
                    toTransaction(userId, detail, request, range)))))
            .then(Mono.defer(() -> requireDetail(userId, id, month, year, range)))
            .map(this::toOccurrence));
  }

  @Override
  public Mono<RecurringOccurrence> skipRecurring(Long userId, Long id, Integer month,
                                                 Integer year) {
    return resolveMonth(month, year)
        .flatMap(range -> requireDetail(userId, id, month, year, range)
            .flatMap(detail -> requireSkippable(detail)
                .then(Mono.defer(() -> recurringSkipRepository.insertIfAbsent(id, month, year))))
            .then(Mono.defer(() -> requireDetail(userId, id, month, year, range)))
            .map(this::toOccurrence));
  }

  /**
   * {@inheritDoc}
   *
   * <p>No comprueba si el mes estaba omitido ni si el fijo vence en él: deshacer algo que
   * no estaba hecho no es un error, y exigir que venza dejaría atrapada la omisión de una
   * plantilla que después se pausó.</p>
   */
  @Override
  public Mono<RecurringOccurrence> unskipRecurring(Long userId, Long id, Integer month,
                                                   Integer year) {
    return resolveMonth(month, year)
        .flatMap(range -> requireDetail(userId, id, month, year, range)
            .then(Mono.defer(() -> recurringSkipRepository.deleteByPeriod(id, month, year)))
            .then(Mono.defer(() -> requireDetail(userId, id, month, year, range)))
            .map(this::toOccurrence));
  }

  /**
   * Decide en qué estado está una plantilla dentro del mes contra el que se leyó.
   *
   * <p>El orden de las comprobaciones es el de la fuerza de cada cosa. Un movimiento
   * enlazado es un hecho y manda sobre todo lo demás: si el fijo se pagó en septiembre y
   * después se pausó o se pasó a bimestral, septiembre sigue estando pagado y decir NOT_DUE
   * sería esconderlo. Después va no vencer, que tapa una omisión antigua de una plantilla
   * que ya no toca ese mes. Y solo al final, cuando de verdad toca y está sin resolver,
   * entra la única pregunta que necesita saber qué día es hoy.</p>
   *
   * @param detail plantilla y hechos del mes tal y como los devolvió la consulta
   * @return la plantilla resuelta contra ese mes
   */
  private RecurringOccurrence toOccurrence(RecurringDetail detail) {
    LocalDate dueDate = dueDate(detail);
    if (detail.recurringTransactionId() != null) {
      return new RecurringOccurrence(detail, dueDate, RecurringState.PAID);
    }
    if (!Boolean.TRUE.equals(detail.recurringDue())) {
      return new RecurringOccurrence(detail, null, RecurringState.NOT_DUE);
    }
    if (Boolean.TRUE.equals(detail.recurringSkipped())) {
      return new RecurringOccurrence(detail, dueDate, RecurringState.SKIPPED);
    }
    RecurringState state = dueDate.isBefore(LocalDate.now(clock))
        ? RecurringState.OVERDUE
        : RecurringState.PENDING;
    return new RecurringOccurrence(detail, dueDate, state);
  }

  /**
   * Calcula el día en que vence una plantilla dentro de su mes.
   * El día previsto se recorta a la longitud del mes: un cargo del 31 vence el 28 en
   * febrero, que es cuando lo cobran de verdad.
   *
   * @param detail plantilla resuelta contra un mes
   * @return el día concreto de vencimiento
   */
  private LocalDate dueDate(RecurringDetail detail) {
    YearMonth period = YearMonth.of(detail.recurringYear(), detail.recurringMonth());
    return period.atDay(Math.min(detail.recurringDayOfMonth(), period.lengthOfMonth()));
  }

  /**
   * Construye el movimiento con el que se confirma un mes.
   * Lo que la petición no diga se toma de la plantilla, que es justo lo que permite
   * confirmar de un toque cuando se pagó lo previsto.
   *
   * @param userId  identificador del usuario propietario
   * @param detail  plantilla resuelta contra el mes que se confirma
   * @param request datos con los que se confirma
   * @param range   rango de fechas del mes que se confirma
   * @return el movimiento listo para guardarse
   */
  private Transaction toTransaction(Long userId, RecurringDetail detail,
                                    ConfirmRecurringTransactionRequest request, DateRange range) {
    LocalDateTime date = request.getDate() == null
        ? defaultDate(detail, range)
        : request.getDate();
    if (date.isBefore(range.from()) || date.isAfter(range.to())) {
      throw new RecurringDateOutOfPeriodException(Constants.RECURRING_DATE_OUT_OF_PERIOD);
    }
    Transaction transaction = new Transaction();
    transaction.setUserId(userId);
    transaction.setAmount(request.getAmount() == null
        ? detail.recurringAmount()
        : request.getAmount());
    transaction.setDescription(request.getDescription() == null
        ? detail.recurringDescription()
        : request.getDescription());
    transaction.setDate(date);
    transaction.setTransactionTypeId(detail.recurringTypeId());
    transaction.setCategoryId(detail.recurringCategoryId());
    transaction.setRecurringId(detail.recurringId());
    return transaction;
  }

  /**
   * Fecha con la que se registra el movimiento cuando la petición no indica ninguna.
   *
   * <p>Si hoy cae dentro del mes que se confirma se usa hoy, porque es cuando de verdad se
   * pagó. Si no, el día previsto de ese mes: usar hoy siempre sacaría de septiembre al
   * alquiler confirmado el 3 de octubre, y el mes dejaría de cuadrar con lo que muestra la
   * pantalla de fijos.</p>
   *
   * @param detail plantilla resuelta contra el mes que se confirma
   * @param range  rango de fechas de ese mes
   * @return la fecha con la que se registra el movimiento
   */
  private LocalDateTime defaultDate(RecurringDetail detail, DateRange range) {
    LocalDateTime now = LocalDateTime.now(clock);
    if (!now.isBefore(range.from()) && !now.isAfter(range.to())) {
      return now;
    }
    return dueDate(detail).atStartOfDay();
  }

  /**
   * Construye la plantilla a persistir a partir de la petición de alta.
   * Nace activa: dar de alta un fijo ya pausado no tendría sentido, y pausarlo es una
   * modificación posterior.
   *
   * @param userId  identificador del usuario propietario
   * @param request datos de la plantilla a crear
   * @return la entidad lista para guardarse
   */
  private RecurringTransaction toEntity(Long userId, SaveRecurringTransactionRequest request) {
    RecurringTransaction recurring = new RecurringTransaction();
    recurring.setUserId(userId);
    recurring.setCategoryId(request.getCategoryId());
    recurring.setTransactionTypeId(request.getTransactionTypeId());
    recurring.setDescription(request.getDescription());
    recurring.setAmount(request.getAmount());
    recurring.setDayOfMonth(request.getDayOfMonth());
    recurring.setEveryMonths(request.getEveryMonths() == null ? 1 : request.getEveryMonths());
    recurring.setStartMonth(request.getStartMonth());
    recurring.setStartYear(request.getStartYear());
    recurring.setActive(true);
    return recurring;
  }

  /**
   * Aplica sobre la plantilla existente únicamente los valores informados en la petición.
   *
   * @param recurring plantilla a modificar
   * @param request   datos a actualizar
   * @return la plantilla con los cambios aplicados
   */
  private RecurringTransaction applyChanges(RecurringTransaction recurring,
                                            UpdateRecurringTransactionRequest request) {
    if (request.getCategoryId() != null) {
      recurring.setCategoryId(request.getCategoryId());
    }
    if (request.getTransactionTypeId() != null) {
      recurring.setTransactionTypeId(request.getTransactionTypeId());
    }
    if (request.getDescription() != null) {
      recurring.setDescription(request.getDescription());
    }
    if (request.getAmount() != null) {
      recurring.setAmount(request.getAmount());
    }
    if (request.getDayOfMonth() != null) {
      recurring.setDayOfMonth(request.getDayOfMonth());
    }
    if (request.getEveryMonths() != null) {
      recurring.setEveryMonths(request.getEveryMonths());
    }
    if (request.getStartMonth() != null) {
      recurring.setStartMonth(request.getStartMonth());
    }
    if (request.getStartYear() != null) {
      recurring.setStartYear(request.getStartYear());
    }
    if (request.getActive() != null) {
      recurring.setActive(request.getActive());
    }
    return recurring;
  }

  /**
   * Comprueba las referencias con las que quedará la plantilla después de modificarla.
   * Se validan como pareja y con los valores efectivos, no con los recibidos, por el mismo
   * motivo que en las transacciones: pasar un egreso a ingreso sin tocar la categoría
   * podría dejarla con una que solo admite gastos, y entonces la confirmación fallaría el
   * día que tocara pagarlo, que es el peor momento para enterarse.
   *
   * @param userId    identificador del usuario propietario
   * @param recurring plantilla tal y como está guardada
   * @param request   datos a actualizar
   * @return Mono vacío si las referencias son válidas
   */
  private Mono<Void> requireValidReferences(Long userId, RecurringTransaction recurring,
                                            UpdateRecurringTransactionRequest request) {
    if (request.getCategoryId() == null && request.getTransactionTypeId() == null) {
      return Mono.empty();
    }
    Long categoryId = request.getCategoryId() == null
        ? recurring.getCategoryId()
        : request.getCategoryId();
    Long typeId = request.getTransactionTypeId() == null
        ? recurring.getTransactionTypeId()
        : request.getTransactionTypeId();
    return requireUsablePair(userId, categoryId, typeId);
  }

  /**
   * Comprueba que la categoría exista, sea del usuario y admita ese tipo de movimiento.
   * Es la misma comprobación que al registrar un movimiento a mano, y por eso comparte la
   * regla de {@link CategoryRules}: si un fijo pudiera guardarse con una pareja que el
   * registro rechaza, no habría forma de confirmarlo nunca.
   *
   * @param userId     identificador del usuario propietario
   * @param categoryId identificador de la categoría
   * @param typeId     identificador del tipo de movimiento
   * @return Mono vacío si la pareja es válida
   */
  private Mono<Void> requireUsablePair(Long userId, Long categoryId, Long typeId) {
    return requireTransactionType(typeId)
        .flatMap(type -> categoryRepository.findByIdAndUserId(categoryId, userId)
            .switchIfEmpty(Mono.error(
                new CategoryNotFoundException(Constants.CATEGORY_NOT_FOUND + categoryId)))
            .flatMap(category -> CategoryRules.admits(category, type)
                ? Mono.just(category)
                : Mono.error(new CategoryNotApplicableException(
                    Constants.CATEGORY_NOT_APPLICABLE.replace("{}", category.getName())))))
        .then();
  }

  /**
   * Obtiene el tipo de movimiento indicado o falla si no existe.
   *
   * @param typeId identificador del tipo de movimiento
   * @return el tipo encontrado
   */
  private Mono<TransactionType> requireTransactionType(Long typeId) {
    return transactionTypeRepository.findById(typeId)
        .switchIfEmpty(Mono.error(new TransactionTypeNotFoundException(
            Constants.TRANSACTION_TYPE_NOT_FOUND + typeId)));
  }

  /**
   * Obtiene una plantilla del usuario o falla si no existe.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la plantilla
   * @return la plantilla encontrada
   */
  private Mono<RecurringTransaction> requireRecurring(Long userId, Long id) {
    return recurringRepository.findByIdAndUserId(id, userId)
        .switchIfEmpty(Mono.error(
            new RecurringNotFoundException(Constants.RECURRING_NOT_FOUND + id)));
  }

  /**
   * Obtiene una plantilla del usuario resuelta contra un mes, o falla si no existe.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la plantilla
   * @param month  mes contra el que se resuelve
   * @param year   año contra el que se resuelve
   * @param range  rango de fechas de ese mes
   * @return la plantilla con los hechos de ese mes
   */
  private Mono<RecurringDetail> requireDetail(Long userId, Long id, Integer month, Integer year,
                                              DateRange range) {
    return recurringRepository.findDetailById(userId, id, month, year, range.from(), range.to())
        .switchIfEmpty(Mono.error(
            new RecurringNotFoundException(Constants.RECURRING_NOT_FOUND + id)));
  }

  /**
   * Comprueba que un mes se pueda confirmar.
   *
   * <p>Un mes ya confirmado se rechaza antes que nada: registrar un segundo movimiento
   * contaría el mismo alquiler dos veces en el resumen. Una omisión y una confirmación son
   * decisiones contrarias sobre el mismo mes, y resolverlo en silencio a favor de
   * cualquiera de las dos dejaría al usuario sin saber cuál ganó.</p>
   *
   * @param detail plantilla resuelta contra el mes que se quiere confirmar
   * @return Mono vacío si el mes se puede confirmar
   */
  private Mono<Void> requireConfirmable(RecurringDetail detail) {
    if (detail.recurringTransactionId() != null) {
      return Mono.error(new RecurringAlreadyConfirmedException(
          Constants.RECURRING_ALREADY_CONFIRMED.replace("{}", detail.recurringDescription())));
    }
    if (Boolean.TRUE.equals(detail.recurringSkipped())) {
      return Mono.error(new RecurringSkippedException(
          Constants.RECURRING_SKIPPED.replace("{}", detail.recurringDescription())));
    }
    return requireDue(detail);
  }

  /**
   * Comprueba que un mes se pueda omitir.
   * Lo que ya se pagó no puede declararse como que no tocaba: para eso hay que borrar el
   * movimiento.
   *
   * @param detail plantilla resuelta contra el mes que se quiere omitir
   * @return Mono vacío si el mes se puede omitir
   */
  private Mono<Void> requireSkippable(RecurringDetail detail) {
    if (detail.recurringTransactionId() != null) {
      return Mono.error(new RecurringAlreadyConfirmedException(
          Constants.RECURRING_ALREADY_CONFIRMED.replace("{}", detail.recurringDescription())));
    }
    return requireDue(detail);
  }

  /**
   * Comprueba que la plantilla venza en el mes contra el que se leyó.
   *
   * @param detail plantilla resuelta contra un mes
   * @return Mono vacío si vence en él
   */
  private Mono<Void> requireDue(RecurringDetail detail) {
    return Boolean.TRUE.equals(detail.recurringDue())
        ? Mono.empty()
        : Mono.error(new RecurringNotDueException(
            Constants.RECURRING_NOT_DUE.replace("{}", detail.recurringDescription())));
  }

  /**
   * Traduce el mes natural al rango de fechas que abarca.
   *
   * <p>Va dentro del flujo y no antes de construirlo para que un mes imposible llegue como
   * un error del publicador y no como una excepción lanzada al pedirlo, igual que en los
   * presupuestos.</p>
   *
   * @param month mes solicitado
   * @param year  año solicitado
   * @return el rango de fechas del mes
   */
  private Mono<DateRange> resolveMonth(Integer month, Integer year) {
    return Mono.fromCallable(() -> DateRanges.resolve(month, year, null, null));
  }
}
