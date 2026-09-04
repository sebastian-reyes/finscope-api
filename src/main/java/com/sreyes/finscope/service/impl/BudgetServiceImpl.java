package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.exception.custom.BudgetAlreadySetException;
import com.sreyes.finscope.exception.custom.BudgetNotFoundException;
import com.sreyes.finscope.exception.custom.CategoryNotApplicableException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.model.entity.Budget;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.query.BudgetProgress;
import com.sreyes.finscope.model.query.DateRange;
import com.sreyes.finscope.repository.BudgetRepository;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.service.BudgetService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.query.DateRanges;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link BudgetService}.
 *
 * El presupuesto es el plan y las transacciones son lo que pasó: aquí no se escribe ni se
 * borra ningún movimiento. Retirar un presupuesto solo quita el límite contra el que la
 * categoría se comparaba.
 *
 * El mes se traduce a un rango de fechas con {@link DateRanges}, el mismo que usan el
 * listado y los resúmenes. Es lo que garantiza que «lo gastado en agosto» signifique
 * exactamente lo mismo en la barra de avance y en el gráfico de reparto.
 */
@Service
@RequiredArgsConstructor
public class BudgetServiceImpl implements BudgetService {

  private final BudgetRepository budgetRepository;
  private final CategoryRepository categoryRepository;

  @Override
  public Flux<BudgetProgress> findBudgets(Long userId, Integer month, Integer year) {
    return resolveMonth(month, year)
        .flatMapMany(range -> budgetRepository.findProgressByPeriod(userId, month, year,
            range.from(), range.to()));
  }

  /**
   * {@inheritDoc}
   *
   * <p>La inserción se apoya en la restricción de unicidad de la base de datos: si no
   * escribe ninguna fila es porque esa categoría ya tenía presupuesto ese mes, y entonces
   * se responde con un conflicto en lugar de sumar los dos importes o pisar el anterior.
   * Que el importe cambie solo debe poder pasar cuando alguien lo pide explícitamente.</p>
   */
  @Override
  public Mono<BudgetProgress> createBudget(Long userId, Long categoryId, Integer month,
                                           Integer year, BigDecimal amount) {
    // Resolver el rango antes de escribir valida de paso el mes: uno fuera de rango falla
    // aquí y no después de haber insertado la fila.
    return resolveMonth(month, year)
        .flatMap(range -> requireBudgetableCategory(userId, categoryId)
            .flatMap(category -> budgetRepository
                .insertIfAbsent(userId, categoryId, month, year, amount)
                .filter(inserted -> inserted > 0)
                .switchIfEmpty(Mono.error(alreadySet(category.getName())))
                // Diferido: sin esto la consulta se arma aunque la inserción no llegue a
                // escribir nada y el flujo termine en el conflicto de arriba.
                .then(Mono.defer(() -> budgetRepository.findByCategoryAndPeriod(userId,
                    categoryId, month, year))))
            .flatMap(budget -> budgetRepository.findProgressById(userId, budget.getId(),
                month, year, range.from(), range.to())));
  }

  @Override
  public Mono<BudgetProgress> updateBudget(Long userId, Long id, BigDecimal amount) {
    return requireBudget(userId, id)
        .flatMap(budget -> {
          budget.setAmount(amount);
          return budgetRepository.save(budget);
        })
        .flatMap(budget -> progressOf(userId, budget));
  }

  @Override
  public Mono<Void> deleteBudget(Long userId, Long id) {
    return requireBudget(userId, id).flatMap(budgetRepository::delete);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Se devuelve el mes destino entero y no solo lo copiado, porque lo que el usuario
   * necesita ver después de copiar es cómo queda el plan del mes, no cuántas filas se
   * escribieron.</p>
   */
  @Override
  public Flux<BudgetProgress> copyBudgets(Long userId, Integer sourceMonth, Integer sourceYear,
                                          Integer month, Integer year) {
    // Los dos meses se validan antes de escribir nada, aunque solo el destino se consulte
    // después: copiar desde un mes imposible es un error de la petición, no una copia vacía.
    return resolveMonth(sourceMonth, sourceYear)
        .then(resolveMonth(month, year))
        .flatMapMany(range -> budgetRepository
            .copyPeriod(userId, sourceMonth, sourceYear, month, year)
            .thenMany(budgetRepository.findProgressByPeriod(userId, month, year, range.from(),
                range.to())));
  }

  /**
   * Traduce el mes natural al rango de fechas que abarca.
   *
   * <p>Va dentro del flujo y no antes de construirlo para que un mes imposible llegue como
   * un error del publicador y no como una excepción lanzada al pedirlo: quien llama espera
   * un {@code Publisher}, y una excepción ahí se saltaría el manejo de errores de toda la
   * cadena.</p>
   *
   * @param month mes solicitado
   * @param year  año solicitado
   * @return el rango de fechas del mes
   */
  private Mono<DateRange> resolveMonth(Integer month, Integer year) {
    return Mono.fromCallable(() -> DateRanges.resolve(month, year, null, null));
  }

  /**
   * Obtiene el avance de un presupuesto dentro de su propio mes.
   *
   * @param userId identificador del usuario propietario
   * @param budget presupuesto ya guardado
   * @return el presupuesto junto a lo gastado en su categoría durante su mes
   */
  private Mono<BudgetProgress> progressOf(Long userId, Budget budget) {
    return resolveMonth(budget.getMonth(), budget.getYear())
        .flatMap(range -> budgetRepository.findProgressById(userId, budget.getId(),
            budget.getMonth(), budget.getYear(), range.from(), range.to()));
  }

  /**
   * Obtiene un presupuesto del usuario o falla si no existe.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del presupuesto
   * @return el presupuesto encontrado
   */
  private Mono<Budget> requireBudget(Long userId, Long id) {
    return budgetRepository.findByIdAndUserId(id, userId)
        .switchIfEmpty(Mono.error(new BudgetNotFoundException(Constants.BUDGET_NOT_FOUND + id)));
  }

  /**
   * Obtiene la categoría a presupuestar y comprueba que tenga sentido presupuestarla.
   *
   * <p>Una categoría de solo ingresos se rechaza: el avance se mide contra lo gastado, así
   * que su barra no podría moverse nunca y el usuario se quedaría esperando un número que
   * no va a llegar.</p>
   *
   * @param userId     identificador del usuario propietario
   * @param categoryId identificador de la categoría
   * @return la categoría encontrada
   */
  private Mono<Category> requireBudgetableCategory(Long userId, Long categoryId) {
    return categoryRepository.findByIdAndUserId(categoryId, userId)
        .switchIfEmpty(Mono.error(
            new CategoryNotFoundException(Constants.CATEGORY_NOT_FOUND + categoryId)))
        .flatMap(category -> CategoryScope.INCOME.getValue().equals(category.getAppliesTo())
            ? Mono.error(new CategoryNotApplicableException(
                Constants.BUDGET_CATEGORY_NOT_APPLICABLE.replace("{}", category.getName())))
            : Mono.just(category));
  }

  /**
   * Construye el error de categoría ya presupuestada.
   *
   * @param name nombre de la categoría que ya tenía presupuesto ese mes
   * @return la excepción a propagar
   */
  private BudgetAlreadySetException alreadySet(String name) {
    return new BudgetAlreadySetException(Constants.BUDGET_ALREADY_SET.replace("{}", name));
  }
}
