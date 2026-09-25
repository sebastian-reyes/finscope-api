package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.CategorySummaryResponse;
import com.sreyes.finscope.api.model.Currency;
import com.sreyes.finscope.api.model.CurrencySummaryResponse;
import com.sreyes.finscope.api.model.SummaryBucketResponse;
import com.sreyes.finscope.api.model.SummaryGranularity;
import com.sreyes.finscope.api.model.SummarySeriesResponse;
import com.sreyes.finscope.api.model.TagSummaryResponse;
import com.sreyes.finscope.api.model.TransactionSummaryResponse;
import com.sreyes.finscope.api.model.TransactionTypeResponse;
import com.sreyes.finscope.model.query.AmountTotal;
import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.model.query.SummaryBucketSize;
import com.sreyes.finscope.model.query.TransactionSummaryCriteria;
import com.sreyes.finscope.repository.TransactionSummaryRepository;
import com.sreyes.finscope.service.TransactionSummaryService;
import com.sreyes.finscope.util.query.DateRanges;
import com.sreyes.finscope.util.rules.CurrencyRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link TransactionSummaryService}.
 * La suma la hace la base de datos; aquí solo se decide qué significa cada total. Esa
 * decisión es la misma en los cinco agregados: el importe de una transacción se apunta a
 * ingresos o a egresos según el código de su tipo, y el neto es la diferencia entre ambos.
 * Los importes se devuelven con dos decimales aunque el agregado venga con otra escala,
 * para que el cliente reciba siempre la misma forma.
 *
 * <p>Los dos desgloses no son intercambiables y por eso viajan por separado: el de
 * categoría reparte el total del periodo, porque cada transacción tiene exactamente una; el
 * de tag se solapa, porque puede tener varios. Solo el primero admite porcentajes.</p>
 *
 * <p>Ningún total suma monedas distintas. Todo lo que se devuelve habla de la moneda pedida
 * salvo el desglose por moneda, que es donde están todas y el único sitio donde tiene
 * sentido verlas juntas: son cantidades que no se suman entre sí, y presentarlas como una
 * sola cifra sería inventar un importe que nadie tiene.</p>
 */
@Service
@RequiredArgsConstructor
public class TransactionSummaryServiceImpl implements TransactionSummaryService {

  /** Escala monetaria de la aplicación, la misma con la que se guardan los importes. */
  private static final int AMOUNT_SCALE = 2;

  private final TransactionSummaryRepository transactionSummaryRepository;

  @Override
  public Mono<TransactionSummaryResponse> summarize(Long userId,
                                                    TransactionSummaryCriteria criteria) {
    return Mono.fromCallable(() -> resolveRange(criteria))
        .flatMap(range -> Mono.zip(
            transactionSummaryRepository.totalsByType(userId, criteria, range).collectList(),
            transactionSummaryRepository.totalsByCategory(userId, criteria, range).collectList(),
            transactionSummaryRepository.totalsByTag(userId, criteria, range).collectList(),
            transactionSummaryRepository
                .totalsByCurrency(userId, criteria.withoutCurrency(), range).collectList()))
        .map(totals -> buildSummary(criteria, totals.getT1(), totals.getT2(),
            totals.getT3(), totals.getT4()));
  }

  @Override
  public Mono<SummarySeriesResponse> summarizeSeries(Long userId,
                                                     TransactionSummaryCriteria criteria,
                                                     SummaryGranularity granularity) {
    SummaryGranularity requested = granularity == null ? SummaryGranularity.MONTH : granularity;
    return Mono.fromCallable(() -> resolveRange(criteria))
        .flatMapMany(range -> transactionSummaryRepository.totalsByPeriod(userId, criteria, range,
            toBucketSize(requested)))
        .collectList()
        .map(totals -> {
          SummarySeriesResponse series =
              new SummarySeriesResponse(requested, buildBuckets(totals));
          series.setCurrency(currencyOf(criteria));
          return series;
        });
  }

  /**
   * Resuelve el rango de fechas efectivo de los filtros recibidos.
   * Se delega en la misma resolución que usa el listado para que un resumen no pueda
   * interpretar un filtro de forma distinta a como lo interpretaría la consulta.
   *
   * @param criteria filtros solicitados
   * @return el rango de fechas aplicable
   */
  private DateRange resolveRange(TransactionSummaryCriteria criteria) {
    return DateRanges.resolve(criteria.month(), criteria.year(), criteria.dateFrom(),
        criteria.dateTo());
  }

  /**
   * Ensambla el resumen del periodo a partir de los totales por tipo, categoría, tag y
   * moneda.
   *
   * @param criteria   filtros solicitados, de donde sale la moneda de las cifras
   * @param byType     totales agrupados por tipo de transacción
   * @param byCategory totales agrupados por categoría y tipo de transacción
   * @param byTag      totales agrupados por tag y tipo de transacción
   * @param byCurrency totales agrupados por moneda y tipo de transacción
   * @return el resumen del periodo
   */
  private TransactionSummaryResponse buildSummary(TransactionSummaryCriteria criteria,
                                                  List<AmountTotal> byType,
                                                  List<AmountTotal> byCategory,
                                                  List<AmountTotal> byTag,
                                                  List<AmountTotal> byCurrency) {
    Balance balance = Balance.of(byType);
    TransactionSummaryResponse summary = new TransactionSummaryResponse(balance.income(),
        balance.expense(), balance.net(), balance.movements(),
        buildCurrencySummaries(byCurrency), buildCategorySummaries(byCategory),
        buildTagSummaries(byTag));
    Currency currency = currencyOf(criteria);
    summary.setCurrency(currency);
    if (criteria.converted() && !CurrencyRules.isBase(currency)) {
      summary.setRate(criteria.conversionRate());
    }
    return summary;
  }

  /**
   * La moneda en la que hablan las cifras del resumen: la de destino si se convirtió, la
   * del filtro si se acotó, y la base si no se dijo ninguna.
   *
   * @param criteria filtros solicitados
   * @return la moneda de los totales
   */
  private static Currency currencyOf(TransactionSummaryCriteria criteria) {
    if (criteria.converted()) {
      return Currency.fromValue(criteria.convertTo());
    }
    return criteria.currency() == null
        ? CurrencyRules.BASE
        : Currency.fromValue(criteria.currency());
  }

  /**
   * Agrupa por moneda los totales que la base de datos devuelve separados por tipo.
   *
   * <p>El orden no lo marca el importe, como en los demás desgloses, porque importes de
   * monedas distintas no se pueden comparar: mil soles no son más ni menos que quinientos
   * dólares mientras nadie los convierta. Manda la moneda base y detrás van las otras por
   * orden alfabético, de modo que la lista no baile entre llamadas y la de casa quede
   * siempre en el mismo sitio.</p>
   *
   * @param byCurrency totales agrupados por moneda y tipo de transacción
   * @return el desglose por moneda, ordenado
   */
  private List<CurrencySummaryResponse> buildCurrencySummaries(List<AmountTotal> byCurrency) {
    Map<String, List<AmountTotal>> grouped = new LinkedHashMap<>();
    for (AmountTotal total : byCurrency) {
      grouped.computeIfAbsent(total.currency(), code -> new ArrayList<>()).add(total);
    }
    List<CurrencySummaryResponse> summaries = new ArrayList<>();
    grouped.forEach((code, totals) -> {
      Balance balance = Balance.of(totals);
      summaries.add(new CurrencySummaryResponse(Currency.fromValue(code), balance.income(),
          balance.expense(), balance.net(), balance.movements()));
    });
    summaries.sort(Comparator
        .comparing((CurrencySummaryResponse summary) ->
            CurrencyRules.isBase(summary.getCurrency()) ? 0 : 1)
        .thenComparing(summary -> summary.getCurrency().getValue()));
    return summaries;
  }

  /**
   * Agrupa por categoría los totales que la base de datos devuelve separados por tipo.
   * El orden lo marca el egreso, de mayor a menor, porque la pregunta que responde este
   * desglose es en qué se está gastando; a igualdad de egreso decide el ingreso, y el
   * nombre desempata para que el listado no baile entre llamadas.
   *
   * @param byCategory totales agrupados por categoría y tipo de transacción
   * @return el desglose por categoría, ordenado
   */
  private List<CategorySummaryResponse> buildCategorySummaries(List<AmountTotal> byCategory) {
    Map<Long, List<AmountTotal>> grouped = new LinkedHashMap<>();
    for (AmountTotal total : byCategory) {
      grouped.computeIfAbsent(total.categoryId(), id -> new ArrayList<>()).add(total);
    }
    List<CategorySummaryResponse> summaries = new ArrayList<>();
    grouped.forEach((categoryId, totals) -> {
      Balance balance = Balance.of(totals);
      summaries.add(new CategorySummaryResponse(categoryId, totals.getFirst().categoryName(),
          balance.income(), balance.expense(), balance.movements()));
    });
    summaries.sort(Comparator
        .comparing(CategorySummaryResponse::getExpense, Comparator.reverseOrder())
        .thenComparing(CategorySummaryResponse::getIncome, Comparator.reverseOrder())
        .thenComparing(summary -> Objects.toString(summary.getCategory(), "")));
    return summaries;
  }

  /**
   * Agrupa por tag los totales que la base de datos devuelve separados por tipo.
   * El orden lo marca el egreso, de mayor a menor, porque la pregunta que responde este
   * desglose es en qué se está gastando; a igualdad de egreso decide el ingreso, y el
   * nombre desempata para que el listado no baile entre llamadas.
   *
   * @param byTag totales agrupados por tag y tipo de transacción
   * @return el desglose por tag, ordenado
   */
  private List<TagSummaryResponse> buildTagSummaries(List<AmountTotal> byTag) {
    Map<String, List<AmountTotal>> grouped = new LinkedHashMap<>();
    for (AmountTotal total : byTag) {
      grouped.computeIfAbsent(total.tagName(), name -> new ArrayList<>()).add(total);
    }
    List<TagSummaryResponse> summaries = new ArrayList<>();
    grouped.forEach((tagName, totals) -> {
      Balance balance = Balance.of(totals);
      TagSummaryResponse summary =
          new TagSummaryResponse(balance.income(), balance.expense(), balance.movements());
      summary.setTag(tagName);
      summaries.add(summary);
    });
    summaries.sort(Comparator
        .comparing(TagSummaryResponse::getExpense, Comparator.reverseOrder())
        .thenComparing(TagSummaryResponse::getIncome, Comparator.reverseOrder())
        .thenComparing(summary -> Objects.toString(summary.getTag(), "")));
    return summaries;
  }

  /**
   * Ensambla los tramos de la serie agrupando por su instante inicial.
   *
   * @param byPeriod totales agrupados por tramo y tipo de transacción, en orden cronológico
   * @return los tramos con alguna transacción, en orden cronológico
   */
  private List<SummaryBucketResponse> buildBuckets(List<AmountTotal> byPeriod) {
    Map<LocalDateTime, List<AmountTotal>> grouped = new LinkedHashMap<>();
    for (AmountTotal total : byPeriod) {
      grouped.computeIfAbsent(total.periodStart(), start -> new ArrayList<>()).add(total);
    }
    List<SummaryBucketResponse> buckets = new ArrayList<>();
    grouped.forEach((periodStart, totals) -> {
      Balance balance = Balance.of(totals);
      buckets.add(new SummaryBucketResponse(periodStart, balance.income(), balance.expense(),
          balance.net(), balance.movements()));
    });
    return buckets;
  }

  /**
   * Traduce la granularidad del contrato al tamaño de tramo que entiende la consulta.
   *
   * @param granularity granularidad solicitada
   * @return el tamaño de tramo equivalente
   */
  private SummaryBucketSize toBucketSize(SummaryGranularity granularity) {
    return switch (granularity) {
      case DAY -> SummaryBucketSize.DAY;
      case WEEK -> SummaryBucketSize.WEEK;
      case MONTH -> SummaryBucketSize.MONTH;
    };
  }

  /**
   * Reparto de un conjunto de totales entre ingresos y egresos.
   * Es el único sitio donde se interpreta el código del tipo, de modo que el periodo
   * completo, cada categoría, cada tag y cada tramo de la serie calculan su saldo de la
   * misma manera.
   *
   * @param income    suma de los importes de tipo INCOME
   * @param expense   suma de los importes de tipo EXPENSE
   * @param movements cuántas transacciones componen el conjunto
   */
  private record Balance(BigDecimal income, BigDecimal expense, long movements) {

    /**
     * Reparte los totales según el código del tipo al que pertenecen.
     *
     * @param totals totales a repartir
     * @return el saldo resultante
     */
    private static Balance of(List<AmountTotal> totals) {
      BigDecimal income = BigDecimal.ZERO;
      BigDecimal expense = BigDecimal.ZERO;
      long movements = 0L;
      for (AmountTotal total : totals) {
        if (TransactionTypeResponse.CodeEnum.INCOME.getValue().equals(total.typeCode())) {
          income = income.add(total.total());
        } else {
          expense = expense.add(total.total());
        }
        movements += total.movements();
      }
      return new Balance(scaled(income), scaled(expense), movements);
    }

    /**
     * Diferencia entre lo ingresado y lo gastado, negativa si se gastó de más.
     *
     * @return el saldo neto
     */
    private BigDecimal net() {
      return scaled(income.subtract(expense));
    }

    /**
     * Ajusta un importe a la escala monetaria de la aplicación.
     *
     * @param amount importe a ajustar
     * @return el importe con dos decimales
     */
    private static BigDecimal scaled(BigDecimal amount) {
      return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
  }
}
