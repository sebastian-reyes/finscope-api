package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.CreateTransactionRequest;
import com.sreyes.finscope.api.model.Currency;
import com.sreyes.finscope.api.model.UpdateTransactionRequest;
import com.sreyes.finscope.exception.custom.CategoryNotApplicableException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.entity.Category;
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
import com.sreyes.finscope.util.patch.Patches;
import com.sreyes.finscope.util.rules.CategoryRules;
import com.sreyes.finscope.util.rules.CurrencyRules;
import com.sreyes.finscope.util.rules.TagRules;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link TransactionCommandService} para la gestión de comandos
 * de transacciones.
 * Proporciona operaciones reactivas para crear, actualizar y eliminar transacciones.
 * Hay dos referencias que validar, el tipo y la categoría, y no se validan por separado:
 * la categoría declara a qué tipo de movimiento se ofrece, así que se comprueban como
 * pareja. Los tags, en cambio, son texto libre y, si el usuario escribe uno que todavía no
 * tiene, se da de alta en su catálogo sobre la marcha.
 *
 * <p>La moneda y su tipo de cambio se comprueban también como pareja, y por el mismo
 * motivo: por separado cada uno es válido y juntos pueden describir algo que no existe. La
 * regla vive en {@link CurrencyRules}.</p>
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
    List<String> tags = TagRules.normalize(request.getTags());
    return requireTransactionType(request.getTransactionTypeId())
        .flatMap(type -> requireUsableCategory(userId, request.getCategoryId(), type))
        .then(Mono.defer(() -> {
          // Dentro del flujo y no antes de montarlo: un fallo aqui tiene que viajar como
          // error del Mono, igual que el del tipo o el de la categoria, y no escaparse
          // mientras el llamante todavia esta construyendo la cadena.
          CurrencyRules.validate(CurrencyRules.orBase(request.getCurrency()),
              request.getExchangeRate());
          return transactionRepository.save(toEntity(userId, request));
        }))
        .flatMap(saved -> replaceTags(userId, saved.getId(), tags).thenReturn(saved));
  }

  @Override
  public Mono<Transaction> updateTransaction(Long userId, Long id,
                                             UpdateTransactionRequest request) {
    List<String> tags = TagRules.normalize(request.getTags());
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
   * Si la petición no indica fecha se registra el instante actual, y si no indica moneda se
   * registra la base.
   *
   * @param userId  identificador del usuario propietario
   * @param request datos de la transacción a crear
   * @return la entidad lista para guardarse
   */
  private Transaction toEntity(Long userId, CreateTransactionRequest request) {
    Transaction transaction = new Transaction();
    transaction.setUserId(userId);
    transaction.setAmount(request.getAmount());
    transaction.setCurrency(CurrencyRules.orBase(request.getCurrency()).getValue());
    transaction.setExchangeRate(request.getExchangeRate());
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
    Patches.setIfPresent(request.getAmount(), transaction::setAmount);
    Patches.setIfPresent(request.getDescription(), transaction::setDescription);
    Patches.setIfPresent(request.getDate(), transaction::setDate);
    Patches.setIfPresent(request.getTransactionTypeId(), transaction::setTransactionTypeId);
    Patches.setIfPresent(request.getCategoryId(), transaction::setCategoryId);
    applyCurrency(transaction, request);
    return transaction;
  }

  /**
   * Asienta la moneda y el tipo de cambio con los que queda el movimiento.
   *
   * <p>No pueden copiarse campo a campo como los demás: para saber si el cambio recibido es
   * válido hay que mirar la moneda con la que va a quedar el movimiento, y para saber si el
   * cambio guardado sigue sirviendo hay que mirar si la moneda cambia. Por eso la moneda
   * manda y el cambio la sigue.</p>
   *
   * <p>Cambiar de moneda descarta el tipo de cambio anterior aunque la petición no lo
   * mencione, porque era el de la moneda anterior: reaprovecharlo convertiría dólares con
   * el cambio de los euros. Así, pasar a la moneda base lo borra sin que haya que mandar
   * nada —que es lo único que se puede hacer cuando un nulo significa «no lo toques»—, y
   * pasar a otra exige mandar el suyo en la misma petición.</p>
   *
   * <p>Cuando la moneda no cambia, el tipo de cambio guardado se conserva mientras la
   * petición no traiga otro. Corregir el importe de una compra en dólares no vuelve a
   * preguntar con qué cambio se apuntó, y el histórico se queda como estaba.</p>
   *
   * @param transaction transacción a modificar
   * @param request     datos a actualizar
   */
  private void applyCurrency(Transaction transaction, UpdateTransactionRequest request) {
    Currency current = Currency.fromValue(transaction.getCurrency());
    Currency target = request.getCurrency() == null ? current : request.getCurrency();
    BigDecimal exchangeRate = target == current
        ? Patches.orKeep(request.getExchangeRate(), transaction.getExchangeRate())
        : request.getExchangeRate();
    CurrencyRules.validate(target, exchangeRate);
    transaction.setCurrency(target.getValue());
    transaction.setExchangeRate(exchangeRate);
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
        .flatMap(category -> CategoryRules.admits(category, type)
            ? Mono.just(category)
            : Mono.error(new CategoryNotApplicableException(
                Constants.CATEGORY_NOT_APPLICABLE.replace("{}", category.getName()))));
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
            : TagRules.resolveIds(tagRepository, userId, names)
                .flatMapMany(tagIds -> transactionTagRepository.saveAll(tagIds.stream()
                    .map(tagId -> new TransactionTag(null, transactionId, tagId))
                    .toList()))
                .then()));
  }

}
