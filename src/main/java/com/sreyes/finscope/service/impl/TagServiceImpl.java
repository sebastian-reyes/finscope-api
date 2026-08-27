package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.TagNameAlreadyUsedException;
import com.sreyes.finscope.exception.custom.TagNotFoundException;
import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.model.query.TagUsage;
import com.sreyes.finscope.repository.TagRepository;
import com.sreyes.finscope.repository.TransactionTagRepository;
import com.sreyes.finscope.service.TagService;
import com.sreyes.finscope.util.constants.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link TagService}.
 * Toda operación parte del identificador del usuario, de modo que un tag ajeno se comporta
 * igual que uno inexistente y nadie puede alcanzarlo conociendo su identificador.
 * La unicidad del nombre se comprueba antes de escribir, pero quien la garantiza de verdad
 * es la restricción de la base de datos: entre la comprobación y la escritura cabe otra
 * petición del mismo usuario creando ese mismo nombre.
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

  private final TagRepository tagRepository;
  private final TransactionTagRepository transactionTagRepository;

  @Override
  public Flux<TagUsage> findTags(Long userId) {
    return tagRepository.findUsageByUserId(userId);
  }

  @Override
  public Mono<TagUsage> createTag(Long userId, String name) {
    String trimmed = name.trim();
    return requireNameAvailable(userId, trimmed, null)
        .then(Mono.defer(() -> tagRepository.insertIfAbsent(userId, trimmed)))
        .then(Mono.defer(() -> tagRepository.findByUserIdAndName(userId, trimmed)))
        .switchIfEmpty(Mono.error(alreadyUsed(trimmed)))
        // Un tag recién creado todavía no puede estar en ninguna transacción.
        .map(tag -> new TagUsage(tag.getId(), tag.getName(), 0L));
  }

  @Override
  public Mono<TagUsage> renameTag(Long userId, Long id, String name) {
    String trimmed = name.trim();
    return requireTag(userId, id)
        .flatMap(tag -> requireNameAvailable(userId, trimmed, tag.getId()).thenReturn(tag))
        .flatMap(tag -> {
          tag.setName(trimmed);
          return tagRepository.save(tag);
        })
        .flatMap(this::toUsage);
  }

  @Override
  public Mono<Void> deleteTag(Long userId, Long id) {
    return requireTag(userId, id)
        .flatMap(tag -> transactionTagRepository.deleteByTagId(tag.getId())
            .then(tagRepository.delete(tag)));
  }

  /**
   * Obtiene el tag del usuario o falla si no existe dentro de su catálogo.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador del tag
   * @return el tag encontrado envuelto en Mono
   */
  private Mono<Tag> requireTag(Long userId, Long id) {
    return tagRepository.findByIdAndUserId(id, userId)
        .switchIfEmpty(Mono.error(new TagNotFoundException(Constants.TAG_NOT_FOUND + id)));
  }

  /**
   * Comprueba que el nombre siga libre dentro del catálogo del usuario.
   * Conservar su propio nombre no es un conflicto, por eso se ignora el tag que se está
   * modificando.
   *
   * @param userId    identificador del usuario propietario
   * @param name      nombre a comprobar, ya recortado
   * @param excludeId identificador del tag que se está modificando, nulo al crear
   * @return Mono vacío si el nombre puede usarse
   */
  private Mono<Void> requireNameAvailable(Long userId, String name, Long excludeId) {
    return tagRepository.findByUserIdAndName(userId, name)
        .filter(existing -> !existing.getId().equals(excludeId))
        .flatMap(existing -> Mono.<Void>error(alreadyUsed(name)));
  }

  /**
   * Completa un tag con el número de transacciones que lo llevan.
   * El conteo se toma de la consulta de catálogo en lugar de calcularse aparte, para que el
   * dato salga siempre del mismo sitio que el del listado.
   *
   * @param tag tag ya persistido
   * @return el tag junto a su uso
   */
  private Mono<TagUsage> toUsage(Tag tag) {
    return tagRepository.findUsageByUserId(tag.getUserId())
        .filter(usage -> usage.tagId().equals(tag.getId()))
        .next()
        .defaultIfEmpty(new TagUsage(tag.getId(), tag.getName(), 0L));
  }

  /**
   * Construye el fallo por nombre ya usado indicando el nombre en conflicto.
   *
   * @param name nombre que ya tiene otro tag del usuario
   * @return la excepción a lanzar
   */
  private TagNameAlreadyUsedException alreadyUsed(String name) {
    return new TagNameAlreadyUsedException(Constants.TAG_NAME_ALREADY_USED.replace("{}", name));
  }
}
