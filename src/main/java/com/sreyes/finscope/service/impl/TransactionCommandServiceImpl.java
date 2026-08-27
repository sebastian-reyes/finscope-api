package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.api.model.CreateTransactionRequest;
import com.sreyes.finscope.api.model.UpdateTransactionRequest;
import com.sreyes.finscope.exception.custom.CategoryNotApplicableException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionTag;
import com.sreyes.finscope.model.entity.TransactionType;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.repository.TagRepository;
import com.sreyes.finscope.repository.TransactionRepository;
import com.sreyes.finscope.repository.TransactionTagRepository;
import com.sreyes.finscope.repository.TransactionTypeRepository;
import com.sreyes.finscope.service.TransactionCommandService;
import com.sreyes.finscope.util.constants.Constants;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link TransactionCommandService} para la gestión de comandos
 * de transacciones.
 * Proporciona operaciones reactivas para crear, actualizar y eliminar transacciones.
 * Hay dos referencias que validar, el tipo y la categoría, y no se validan por separado:
 * la categoría declara a qué tipo de movimiento se ofrece, así que se comprueban como
 * pareja. Los tags, en cambio, son texto libre y, si el usuario escribe uno que todavía no
 * tiene, se da de alta en su catálogo sobre la marcha.
 */
@Service
@RequiredArgsConstructor
public class TransactionCommandServiceImpl implements TransactionCommandService {

  private final TransactionRepository transactionRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final CategoryRepository categoryRepository;
  private final TagRepository tagRepository;
  private final TransactionTagRepository transactionTagRepository;
  private final Clock clock;

  @Override
  public Mono<Transaction> createTransaction(Long userId, CreateTransactionRequest request) {
    List<String> tags = normalizeTags(request.getTags());
    return requireTransactionType(request.getTransactionTypeId())
        .flatMap(type -> requireUsableCategory(userId, request.getCategoryId(), type))
        .then(Mono.defer(() -> transactionRepository.save(toEntity(userId, request))))
        .flatMap(saved -> replaceTags(userId, saved.getId(), tags).thenReturn(saved));
  }

  @Override
  public Mono<Transaction> updateTransaction(Long userId, Long id,
                                             UpdateTransactionRequest request) {
    List<String> tags = normalizeTags(request.getTags());
    return transactionRepository.findByIdAndUserId(id, userId)
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)))
        .flatMap(transaction -> requireValidReferences(userId, transaction, request)
            .thenReturn(applyChanges(transaction, request)))
        .flatMap(transactionRepository::save)
        .flatMap(saved -> request.getTags() == null
            ? Mono.just(saved)
            : replaceTags(userId, saved.getId(), tags).thenReturn(saved));
  }

  @Override
  public Mono<Void> deleteTransactionById(Long userId, Long id) {
    return transactionRepository.findByIdAndUserId(id, userId)
        .switchIfEmpty(Mono.error(new TransactionNotFoundException(
            Constants.TRANSACTION_NOT_FOUND + id)))
        .flatMap(transactionRepository::delete);
  }

  /**
   * Construye la entidad a persistir a partir de la petición de creación.
   * Si la petición no indica fecha se registra el instante actual.
   *
   * @param userId  identificador del usuario propietario
   * @param request datos de la transacción a crear
   * @return la entidad lista para guardarse
   */
  private Transaction toEntity(Long userId, CreateTransactionRequest request) {
    Transaction transaction = new Transaction();
    transaction.setUserId(userId);
    transaction.setAmount(request.getAmount());
    transaction.setDescription(request.getDescription());
    transaction.setDate(request.getDate() == null ? LocalDateTime.now(clock) : request.getDate());
    transaction.setTransactionTypeId(request.getTransactionTypeId());
    transaction.setCategoryId(request.getCategoryId());
    return transaction;
  }

  /**
   * Aplica sobre la transacción existente únicamente los valores informados en la petición.
   *
   * @param transaction transacción a modificar
   * @param request     datos a actualizar
   * @return la transacción con los cambios aplicados
   */
  private Transaction applyChanges(Transaction transaction, UpdateTransactionRequest request) {
    if (request.getAmount() != null) {
      transaction.setAmount(request.getAmount());
    }
    if (request.getDescription() != null) {
      transaction.setDescription(request.getDescription());
    }
    if (request.getDate() != null) {
      transaction.setDate(request.getDate());
    }
    if (request.getTransactionTypeId() != null) {
      transaction.setTransactionTypeId(request.getTransactionTypeId());
    }
    if (request.getCategoryId() != null) {
      transaction.setCategoryId(request.getCategoryId());
    }
    return transaction;
  }

  /**
   * Comprueba las referencias con las que quedará la transacción después de actualizarla.
   * Se validan como pareja y con los valores efectivos, no con los recibidos: cambiar el
   * tipo de un egreso a ingreso sin tocar la categoría podría dejarla clasificada con una
   * categoría de egresos, y eso es tan inválido como elegir esa categoría a mano.
   * Si la petición no toca ninguna de las dos, no hay nada que revalidar.
   *
   * @param userId      identificador del usuario propietario
   * @param transaction transacción tal y como está guardada
   * @param request     datos a actualizar
   * @return Mono vacío si las referencias son válidas
   */
  private Mono<Void> requireValidReferences(Long userId, Transaction transaction,
                                            UpdateTransactionRequest request) {
    if (request.getTransactionTypeId() == null && request.getCategoryId() == null) {
      return Mono.empty();
    }
    Long typeId = request.getTransactionTypeId() == null
        ? transaction.getTransactionTypeId()
        : request.getTransactionTypeId();
    Long categoryId = request.getCategoryId() == null
        ? transaction.getCategoryId()
        : request.getCategoryId();
    return requireTransactionType(typeId)
        .flatMap(type -> requireUsableCategory(userId, categoryId, type))
        .then();
  }

  /**
   * Obtiene el tipo de transacción indicado o falla si no existe.
   *
   * @param transactionTypeId identificador del tipo de transacción
   * @return el tipo de transacción encontrado
   */
  private Mono<TransactionType> requireTransactionType(Long transactionTypeId) {
    return transactionTypeRepository.findById(transactionTypeId)
        .switchIfEmpty(Mono.error(new TransactionTypeNotFoundException(
            Constants.TRANSACTION_TYPE_NOT_FOUND + transactionTypeId)));
  }

  /**
   * Obtiene la categoría indicada y comprueba que pueda clasificar ese tipo de movimiento.
   * La búsqueda acota por propietario, de modo que la categoría de otra cuenta se comporta
   * igual que una inexistente.
   *
   * @param userId     identificador del usuario propietario
   * @param categoryId identificador de la categoría
   * @param type       tipo de la transacción
   * @return la categoría encontrada
   */
  private Mono<Category> requireUsableCategory(Long userId, Long categoryId,
                                               TransactionType type) {
    return categoryRepository.findByIdAndUserId(categoryId, userId)
        .switchIfEmpty(Mono.error(new CategoryNotFoundException(
            Constants.CATEGORY_NOT_FOUND + categoryId)))
        .flatMap(category -> admits(category, type)
            ? Mono.just(category)
            : Mono.error(new CategoryNotApplicableException(
                Constants.CATEGORY_NOT_APPLICABLE.replace("{}", category.getName()))));
  }

  /**
   * Decide si una categoría puede clasificar un tipo de movimiento.
   * Una categoría sin ámbito declarado se admite en cualquiera: el campo solo existe para
   * afinar lo que propone el formulario, no para bloquear lo que el usuario ya eligió.
   *
   * @param category categoría elegida
   * @param type     tipo de la transacción
   * @return si la categoría admite ese tipo
   */
  private boolean admits(Category category, TransactionType type) {
    String scope = category.getAppliesTo();
    return scope == null
        || CategoryScope.BOTH.getValue().equals(scope)
        || scope.equals(type.getCode());
  }

  /**
   * Reemplaza por completo los tags de una transacción.
   * Se rehacen los enlaces, no los tags: los que el usuario deja de usar siguen en su
   * catálogo, de modo que conservan su identificador y su grafía si vuelve a escribirlos.
   *
   * @param userId        identificador del usuario propietario
   * @param transactionId identificador de la transacción
   * @param names         nombres de tag ya normalizados
   * @return Mono vacío al completar el reemplazo
   */
  private Mono<Void> replaceTags(Long userId, Long transactionId, List<String> names) {
    return transactionTagRepository.deleteByTransactionId(transactionId)
        .then(Mono.defer(() -> names.isEmpty()
            ? Mono.empty()
            : resolveTagIds(userId, names)
                .flatMapMany(tagIds -> transactionTagRepository.saveAll(tagIds.stream()
                    .map(tagId -> new TransactionTag(null, transactionId, tagId))
                    .toList()))
                .then()));
  }

  /**
   * Resuelve el identificador de cada nombre de tag, dando de alta en el catálogo del
   * usuario los que todavía no existan.
   * El alta se intenta para todos y la base de datos descarta los que ya estaban, así que
   * después basta con leer el catálogo una vez para tener los identificadores de los tags
   * nuevos y de los reutilizados.
   * Cuando el usuario escribe un tag que ya tiene con otra grafía, se reutiliza el
   * existente: `casa` sobre un `Casa` previo no crea un tag nuevo ni renombra el anterior.
   *
   * @param userId identificador del usuario propietario
   * @param names  nombres de tag ya normalizados
   * @return identificadores de los tags correspondientes
   */
  private Mono<List<Long>> resolveTagIds(Long userId, List<String> names) {
    List<String> lowerNames = names.stream()
        .map(name -> name.toLowerCase(Locale.ROOT))
        .toList();
    return Flux.fromIterable(names)
        .concatMap(name -> tagRepository.insertIfAbsent(userId, name))
        .then(tagRepository.findByUserIdAndLowerNameIn(userId, lowerNames)
            .map(Tag::getId)
            .collectList());
  }

  /**
   * Normaliza los tags de la petición recortando los espacios sobrantes, descartando los
   * vacíos y eliminando los repetidos sin distinguir mayúsculas, de modo que `Casa` y
   * `casa` no acaben conviviendo en la misma transacción. Se conserva la primera grafía
   * recibida y el orden de llegada.
   *
   * @param names nombres de tag recibidos, puede ser nulo
   * @return nombres de tag listos para persistirse
   */
  private List<String> normalizeTags(List<String> names) {
    if (names == null) {
      return List.of();
    }
    Map<String, String> distinct = new LinkedHashMap<>();
    names.stream()
        .filter(name -> name != null && !name.isBlank())
        .map(String::trim)
        .forEach(name -> distinct.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
    return List.copyOf(distinct.values());
  }
}
