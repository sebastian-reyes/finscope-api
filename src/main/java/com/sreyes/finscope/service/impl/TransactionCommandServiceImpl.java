package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.api.model.CreateTransactionRequest;
import com.sreyes.finscope.api.model.UpdateTransactionRequest;
import com.sreyes.finscope.exception.custom.TransactionNotFoundException;
import com.sreyes.finscope.exception.custom.TransactionTypeNotFoundException;
import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionTag;
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
 * La única referencia que hay que validar es el tipo de transacción: los tags son texto
 * libre y, si el usuario escribe uno que todavía no tiene, se da de alta en su catálogo
 * sobre la marcha.
 */
@Service
@RequiredArgsConstructor
public class TransactionCommandServiceImpl implements TransactionCommandService {

  private final TransactionRepository transactionRepository;
  private final TransactionTypeRepository transactionTypeRepository;
  private final TagRepository tagRepository;
  private final TransactionTagRepository transactionTagRepository;
  private final Clock clock;

  @Override
  public Mono<Transaction> createTransaction(Long userId, CreateTransactionRequest request) {
    List<String> tags = normalizeTags(request.getTags());
    return requireTransactionType(request.getTransactionTypeId())
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
        .flatMap(transaction -> requireTransactionType(request.getTransactionTypeId())
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
    return transaction;
  }

  /**
   * Verifica que el tipo de transacción indicado exista.
   * Un identificador nulo corresponde a un campo no informado y se omite.
   *
   * @param transactionTypeId identificador del tipo de transacción
   * @return Mono vacío si la referencia es válida
   */
  private Mono<Void> requireTransactionType(Long transactionTypeId) {
    if (transactionTypeId == null) {
      return Mono.empty();
    }
    return transactionTypeRepository.findById(transactionTypeId)
        .switchIfEmpty(Mono.error(new TransactionTypeNotFoundException(
            Constants.TRANSACTION_TYPE_NOT_FOUND + transactionTypeId)))
        .then();
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
