package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.TransactionPageResponse;
import com.sreyes.finscope.api.model.TransactionResponse;
import com.sreyes.finscope.exception.custom.DateNotFoundException;
import com.sreyes.finscope.exception.custom.InvalidSortException;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.model.query.TransactionFilter;
import com.sreyes.finscope.model.query.TransactionSearchCriteria;
import com.sreyes.finscope.model.query.TransactionTagName;
import com.sreyes.finscope.repository.TagRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.repository.TransactionSearchRepository;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import com.sreyes.finscope.service.TransactionQueryService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.mapper.TransactionMapper;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link TransactionQueryService} para la consulta de transacciones.
 * Normaliza los filtros recibidos desde la API, resuelve la paginación mediante
 * {@link TransactionSearchRepository} y ensambla la respuesta cargando en lote los tipos y
 * los tags de la página, de modo que el número de consultas no crece con su tamaño.
 * Todas las consultas están acotadas al usuario propietario de los datos.
 */
@Service
@RequiredArgsConstructor
public class TransactionQueryServiceImpl implements TransactionQueryService {

  /**
   * Campos por los que se admite ordenar, asociados al nombre de la propiedad de la entidad.
   * Actúa como lista blanca: cualquier otro valor se rechaza.
   */
  private static final Map<String, String> SORTABLE_FIELDS =
      Map.of("date", "date", "amount", "amount", "id", "id");

  private final TransactionRepository transactionRepository;
  private final TransactionSearchRepository transactionSearchRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final TagRepository tagRepository;
  private final TransactionMapper transactionMapper;

  @Override
  public Mono<TransactionPageResponse> searchTransactions(Long userId,
                                                          TransactionSearchCriteria criteria) {
    return Mono.fromCallable(() -> buildPageable(criteria))
        .flatMap(pageable -> resolveFilter(userId, criteria)
            .flatMap(filter -> executeSearch(filter, pageable))
            .switchIfEmpty(Mono.fromSupplier(() -> emptyPage(pageable))));
  }

  @Override
  public Mono<TransactionResponse> getTransactionById(Long userId, Long id) {
    return transactionRepository.findByIdAndUserId(id, userId)
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)))
        .flatMap(transaction -> toResponses(List.of(transaction)))
        .map(List::getFirst);
  }

  /**
   * Ejecuta la búsqueda paginada y su conteo total, y ensambla la página de respuesta.
   *
   * @param filter   criterios de búsqueda ya normalizados
   * @param pageable página y ordenamiento solicitados
   * @return página de transacciones envuelta en Mono
   */
  private Mono<TransactionPageResponse> executeSearch(TransactionFilter filter, Pageable pageable) {
    return transactionSearchRepository.search(filter, pageable)
        .collectList()
        .flatMap(this::toResponses)
        .zipWith(transactionSearchRepository.count(filter))
        .map(result -> buildPage(result.getT1(), result.getT2(), pageable));
  }

  /**
   * Normaliza los filtros de la petición en los criterios que entiende el repositorio.
   * Cuando se filtra por tag y ninguna transacción lo lleva, devuelve un Mono vacío para
   * que la búsqueda se resuelva como una página sin resultados.
   *
   * @param userId   identificador del usuario propietario
   * @param criteria filtros solicitados
   * @return criterios normalizados, o vacío si el filtro por tag no puede satisfacerse
   */
  private Mono<TransactionFilter> resolveFilter(Long userId,
                                                TransactionSearchCriteria criteria) {
    return Mono.fromCallable(() -> resolveDateRange(criteria))
        .flatMap(range -> criteria.tag() == null || criteria.tag().isBlank()
            ? Mono.just(buildFilter(userId, criteria, range, null))
            : tagRepository.findTransactionIdsByUserIdAndName(userId, criteria.tag().trim())
                .collectList()
                .filter(ids -> !ids.isEmpty())
                .map(ids -> buildFilter(userId, criteria, range, ids)));
  }

  /**
   * Compone los criterios del repositorio a partir de los filtros de la petición.
   *
   * @param userId         identificador del usuario propietario
   * @param criteria       filtros solicitados
   * @param range          rango de fechas ya resuelto
   * @param transactionIds restricción por tag, o nulo si no se filtra por tag
   * @return los criterios de búsqueda normalizados
   */
  private TransactionFilter buildFilter(Long userId, TransactionSearchCriteria criteria,
                                        DateRange range, List<Long> transactionIds) {
    return new TransactionFilter(userId, range.from(), range.to(),
        criteria.transactionTypeId(), transactionIds);
  }

  /**
   * Resuelve el rango de fechas efectivo a partir de los filtros recibidos.
   * Los filtros de mes y año son un atajo para acotar a un mes natural completo y no pueden
   * combinarse con un rango explícito de fechas.
   *
   * @param criteria filtros solicitados
   * @return el rango de fechas aplicable, con extremos nulos si no se filtra por fecha
   */
  private DateRange resolveDateRange(TransactionSearchCriteria criteria) {
    boolean hasMonthFilter = criteria.month() != null || criteria.year() != null;
    boolean hasRangeFilter = criteria.dateFrom() != null || criteria.dateTo() != null;

    if (hasMonthFilter && hasRangeFilter) {
      throw new DateNotFoundException(Constants.CONFLICTING_DATE_FILTERS);
    }
    if (hasMonthFilter) {
      return resolveMonthRange(criteria.month(), criteria.year());
    }
    if (criteria.dateFrom() != null && criteria.dateTo() != null
        && criteria.dateFrom().isAfter(criteria.dateTo())) {
      throw new DateNotFoundException(Constants.INVALID_DATE_RANGE);
    }
    return new DateRange(criteria.dateFrom(), criteria.dateTo());
  }

  /**
   * Convierte el filtro de mes y año en el rango de fechas que abarca ese mes completo.
   *
   * @param month mes solicitado
   * @param year  año solicitado
   * @return el rango de fechas correspondiente al mes indicado
   */
  private DateRange resolveMonthRange(Integer month, Integer year) {
    if (month == null || year == null) {
      throw new DateNotFoundException(Constants.INCOMPLETE_MONTH_FILTER);
    }
    if (month < 1 || month > 12) {
      throw new DateNotFoundException(Constants.INVALID_MONTH);
    }
    YearMonth yearMonth = YearMonth.of(year, month);
    return new DateRange(yearMonth.atDay(1).atStartOfDay(),
        yearMonth.atEndOfMonth().atTime(LocalTime.MAX));
  }

  /**
   * Construye la paginación solicitada validando el criterio de ordenamiento.
   *
   * @param criteria filtros solicitados
   * @return la paginación y el ordenamiento aplicables
   */
  private Pageable buildPageable(TransactionSearchCriteria criteria) {
    return PageRequest.of(criteria.page(), criteria.size(), parseSort(criteria.sort()));
  }

  /**
   * Interpreta el criterio de ordenamiento con formato campo,direccion.
   * Solo se admiten los campos de la lista blanca, lo que evita que la petición influya
   * directamente sobre la consulta generada.
   *
   * @param sort criterio de ordenamiento solicitado
   * @return el ordenamiento aplicable
   */
  private Sort parseSort(String sort) {
    if (sort == null || sort.isBlank()) {
      return TransactionSearchRepository.defaultSort();
    }
    String[] parts = sort.split(",");
    if (parts.length != 2) {
      throw new InvalidSortException(Constants.INVALID_SORT);
    }
    String field = SORTABLE_FIELDS.get(parts[0].trim().toLowerCase());
    if (field == null) {
      throw new InvalidSortException(Constants.INVALID_SORT);
    }
    return Sort.by(parseDirection(parts[1]), field);
  }

  /**
   * Interpreta la dirección del ordenamiento.
   *
   * @param direction dirección solicitada
   * @return la dirección de ordenamiento correspondiente
   */
  private Sort.Direction parseDirection(String direction) {
    return switch (direction.trim().toLowerCase()) {
      case "asc" -> Sort.Direction.ASC;
      case "desc" -> Sort.Direction.DESC;
      default -> throw new InvalidSortException(Constants.INVALID_SORT);
    };
  }

  /**
   * Ensambla la representación de cada transacción cargando en una sola consulta por tabla
   * los tipos y los tags de la página.
   *
   * @param transactions transacciones a representar
   * @return listado de representaciones completas envuelto en Mono
   */
  private Mono<List<TransactionResponse>> toResponses(List<Transaction> transactions) {
    if (transactions.isEmpty()) {
      return Mono.just(List.of());
    }
    Set<Long> transactionTypeIds = collectIds(transactions, Transaction::getTransactionTypeId);
    Set<Long> transactionIds = collectIds(transactions, Transaction::getId);

    return Mono.zip(
        transactionTypeRepository.findAllById(transactionTypeIds)
            .collectMap(TransactionType::getId),
        tagRepository.findNamesByTransactionIdIn(transactionIds)
            .collect(Collectors.groupingBy(TransactionTagName::transactionId,
                Collectors.mapping(TransactionTagName::tagName, Collectors.toList()))))
        .map(references -> transactions.stream()
            .map(transaction -> transactionMapper.toResponse(
                transaction,
                references.getT1().get(transaction.getTransactionTypeId()),
                sortedTags(references.getT2().get(transaction.getId()))))
            .toList());
  }

  /**
   * Ordena alfabéticamente los tags de una transacción, sin distinguir mayúsculas, para
   * que el cliente los reciba siempre en el mismo orden.
   *
   * @param tags tags de la transacción, nulo si no tiene ninguno
   * @return los tags ordenados
   */
  private List<String> sortedTags(List<String> tags) {
    return tags == null
        ? List.of()
        : tags.stream().sorted(Comparator.comparing(String::toLowerCase)).toList();
  }

  /**
   * Extrae los identificadores no nulos de un conjunto de transacciones.
   *
   * @param transactions transacciones de origen
   * @param extractor    función que obtiene el identificador de cada transacción
   * @return conjunto de identificadores distintos
   */
  private Set<Long> collectIds(List<Transaction> transactions,
                               Function<Transaction, Long> extractor) {
    return transactions.stream()
        .map(extractor)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * Construye la página de respuesta con sus metadatos de paginación.
   *
   * @param content       transacciones de la página
   * @param totalElements total de transacciones que cumplen los filtros
   * @param pageable      página y ordenamiento solicitados
   * @return la página de respuesta
   */
  private TransactionPageResponse buildPage(List<TransactionResponse> content, long totalElements,
                                            Pageable pageable) {
    int totalPages = (int) Math.ceil((double) totalElements / pageable.getPageSize());
    return new TransactionPageResponse(content, pageable.getPageNumber(), pageable.getPageSize(),
        totalElements, totalPages);
  }

  /**
   * Construye una página sin resultados conservando los metadatos solicitados.
   *
   * @param pageable página y ordenamiento solicitados
   * @return la página vacía
   */
  private TransactionPageResponse emptyPage(Pageable pageable) {
    return buildPage(List.of(), 0L, pageable);
  }

  /**
   * Rango de fechas efectivo de una búsqueda, con ambos extremos inclusivos.
   *
   * @param from fecha inicial, nula si no se filtra por fecha
   * @param to   fecha final, nula si no se filtra por fecha
   */
  private record DateRange(LocalDateTime from, LocalDateTime to) {
  }
}
