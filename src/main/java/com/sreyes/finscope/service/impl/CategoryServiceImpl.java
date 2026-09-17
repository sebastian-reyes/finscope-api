package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.exception.custom.CategoryNameAlreadyUsedException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.SystemCategoryException;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.query.CategoryUsage;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.repository.RecurringTransactionRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.service.CategoryService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.rules.ChipStyleRules;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link CategoryService}.
 *
 * El catálogo es del usuario y se puede editar entero, con una sola excepción: la
 * categoría de reserva no se borra. Es la que recibe los movimientos de las categorías
 * eliminadas, y sin ella una eliminación dejaría transacciones sin categoría, que es
 * obligatoria. Por eso borrar nunca destruye movimientos: los reasigna. Los movimientos
 * fijos siguen a las transacciones y se reasignan igual: perder el alquiler por reordenar
 * el catálogo sería desproporcionado.
 *
 * La unicidad del nombre se comprueba antes de escribir, pero quien la garantiza de
 * verdad es la restricción de la base de datos: entre la comprobación y la escritura cabe
 * otra petición del mismo usuario creando ese mismo nombre.
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

  /** Nombre de la categoría de reserva que recibe lo que queda sin clasificar. */
  private static final String FALLBACK_NAME = "Otros";

  /**
   * Catálogo con el que arranca una cuenta nueva.
   * Es el mismo juego que siembra la migración `V1__categories.sql` para las cuentas que
   * ya existían: aquella lo hace una vez sobre lo que había, esta lo hace en cada alta.
   * Si se toca uno, hay que tocar el otro.
   */
  private static final List<Seed> DEFAULTS = List.of(
      new Seed("Comida", CategoryScope.EXPENSE),
      new Seed("Transporte", CategoryScope.EXPENSE),
      new Seed("Hogar", CategoryScope.EXPENSE),
      new Seed("Salud", CategoryScope.EXPENSE),
      new Seed("Entretenimiento", CategoryScope.EXPENSE),
      new Seed("Educación", CategoryScope.EXPENSE),
      new Seed("Regalos", CategoryScope.EXPENSE),
      new Seed("Compras", CategoryScope.EXPENSE),
      new Seed("Servicios", CategoryScope.EXPENSE),
      new Seed("Salario", CategoryScope.INCOME),
      new Seed("Freelance", CategoryScope.INCOME),
      new Seed("Venta", CategoryScope.INCOME),
      new Seed("Intereses", CategoryScope.INCOME));

  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;
  private final RecurringTransactionRepository recurringTransactionRepository;

  @Override
  public Flux<CategoryUsage> findCategories(Long userId) {
    return categoryRepository.findUsageByUserId(userId);
  }

  @Override
  public Mono<CategoryUsage> createCategory(Long userId, String name, CategoryScope appliesTo,
                                            String color, String icon) {
    String trimmed = name.trim();
    CategoryScope scope = appliesTo == null ? CategoryScope.EXPENSE : appliesTo;
    return requireNameAvailable(userId, trimmed, null)
        .then(Mono.defer(() -> categoryRepository.insertIfAbsent(userId, trimmed,
            scope.getValue(), false)))
        .then(Mono.defer(() -> categoryRepository.findByUserIdAndName(userId, trimmed)))
        .switchIfEmpty(Mono.error(alreadyUsed(trimmed)))
        .flatMap(category -> color == null && icon == null
            ? Mono.just(category)
            : saveStyle(category, color, icon))
        .map(category -> new CategoryUsage(category.getId(), category.getName(),
            category.getAppliesTo(), category.isSystem(), category.getColor(), category.getIcon(),
            0L));
  }

  @Override
  public Mono<CategoryUsage> updateCategory(Long userId, Long id, String name,
                                            CategoryScope appliesTo, String color,
                                            String icon) {
    String trimmed = name.trim();
    return requireCategory(userId, id)
        .flatMap(category -> requireNameAvailable(userId, trimmed, category.getId())
            .thenReturn(category))
        .flatMap(category -> {
          category.setName(trimmed);
          if (appliesTo != null) {
            category.setAppliesTo(appliesTo.getValue());
          }
          applyStyle(category, color, icon);
          return categoryRepository.save(category);
        })
        .flatMap(category -> categoryRepository.findUsageByUserIdAndId(userId, category.getId()));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Las transacciones que clasificaba pasan a la categoría de reserva antes de borrar
   * la fila, porque la clave foránea no permite dejarlas apuntando a algo que ya no
   * existe y perderlas no es una opción.</p>
   *
   * <p>Los movimientos fijos se reasignan con ellas. La base los borraría en cascada, que
   * es lo que sostiene el borrado de una cuenta entera, pero perder el alquiler por
   * reordenar el catálogo sería desproporcionado: se mueven antes de que la cascada tenga
   * nada que llevarse. Los presupuestos, en cambio, sí se van, porque una categoría no
   * puede tener dos en el mismo mes.</p>
   */
  @Override
  @Transactional
  public Mono<Void> deleteCategory(Long userId, Long id) {
    return requireCategory(userId, id)
        .flatMap(category -> category.isSystem()
            ? Mono.error(new SystemCategoryException(Constants.SYSTEM_CATEGORY_PROTECTED))
            : Mono.just(category))
        .flatMap(category -> resolveFallback(userId)
            .flatMap(fallback -> transactionRepository
                .reassignCategory(userId, category.getId(), fallback.getId())
                .then(recurringTransactionRepository.reassignCategory(userId, category.getId(),
                    fallback.getId())))
            .then(categoryRepository.delete(category)));
  }

  @Override
  public Mono<Void> seedDefaults(Long userId) {
    return resolveFallback(userId)
        .thenMany(Flux.fromIterable(DEFAULTS))
        .concatMap(seed -> categoryRepository.insertIfAbsent(userId, seed.name(),
            seed.scope().getValue(), false))
        .then();
  }

  /**
   * Obtiene la categoría de reserva del usuario y la crea si todavía no la tiene.
   * Crearla al vuelo cubre a las cuentas anteriores a esta funcionalidad y hace que
   * eliminar una categoría nunca dependa de que alguien sembrara bien el catálogo.
   *
   * @param userId identificador del usuario propietario
   * @return la categoría de reserva
   */
  private Mono<Category> resolveFallback(Long userId) {
    return categoryRepository.findSystemByUserId(userId)
        .switchIfEmpty(Mono.defer(() -> categoryRepository
            .insertIfAbsent(userId, FALLBACK_NAME, CategoryScope.BOTH.getValue(), true)
            .then(categoryRepository.findSystemByUserId(userId))));
  }

  /**
   * Guarda el color y el icono de una categoría recién dada de alta.
   * El alta en sí no los lleva porque la comparte con la siembra del catálogo inicial, que
   * nunca trae ninguno; quien los elige al crear paga una escritura más.
   *
   * @param category categoría ya persistida
   * @param color    color tal y como llega en la petición, nulo si no se eligió
   * @param icon     icono tal y como llega en la petición, nulo si no se eligió
   * @return la categoría con su aspecto guardado
   */
  private Mono<Category> saveStyle(Category category, String color, String icon) {
    applyStyle(category, color, icon);
    return categoryRepository.save(category);
  }

  /**
   * Aplica el color y el icono recibidos, dejando como está el que no venga.
   *
   * @param category categoría a modificar
   * @param color    color tal y como llega en la petición, nulo para conservarlo
   * @param icon     icono tal y como llega en la petición, nulo para conservarlo
   */
  private void applyStyle(Category category, String color, String icon) {
    if (color != null) {
      category.setColor(ChipStyleRules.toStored(color));
    }
    if (icon != null) {
      category.setIcon(ChipStyleRules.toStored(icon));
    }
  }

  /**
   * Obtiene una categoría del usuario o falla si no existe.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la categoría
   * @return la categoría encontrada
   */
  private Mono<Category> requireCategory(Long userId, Long id) {
    return categoryRepository.findByIdAndUserId(id, userId)
        .switchIfEmpty(Mono.error(
            new CategoryNotFoundException(Constants.CATEGORY_NOT_FOUND + id)));
  }

  /**
   * Comprueba que el nombre no lo ocupe ya otra categoría del usuario.
   *
   * @param userId    identificador del usuario propietario
   * @param name      nombre a comprobar
   * @param currentId identificador de la categoría que se está editando, nulo al crear
   * @return Mono vacío si el nombre está libre
   */
  private Mono<Void> requireNameAvailable(Long userId, String name, Long currentId) {
    return categoryRepository.findByUserIdAndName(userId, name)
        .filter(existing -> !Objects.equals(existing.getId(), currentId))
        .flatMap(existing -> Mono.error(alreadyUsed(name)))
        .then();
  }

  /**
   * Construye el error de nombre ocupado.
   *
   * @param name nombre que ya está en uso
   * @return la excepción a propagar
   */
  private CategoryNameAlreadyUsedException alreadyUsed(String name) {
    return new CategoryNameAlreadyUsedException(
        Constants.CATEGORY_NAME_ALREADY_USED.replace("{}", name));
  }

  /**
   * Categoría del catálogo inicial.
   *
   * @param name  nombre de la categoría
   * @param scope tipo de movimiento al que se ofrece
   */
  private record Seed(String name, CategoryScope scope) {
  }
}
