package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.TagNameAlreadyUsedException;
import com.sreyes.finscope.exception.custom.TagNotFoundException;
import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.model.query.TagUsage;
import com.sreyes.finscope.repository.RecurringTagRepository;
import com.sreyes.finscope.repository.TagRepository;
import com.sreyes.finscope.repository.TransactionTagRepository;
import com.sreyes.finscope.service.TagService;
import com.sreyes.finscope.util.constants.Constants;
import com.sreyes.finscope.util.rules.ChipStyleRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
  private final RecurringTagRepository recurringTagRepository;

  @Override
  public Flux<TagUsage> findTags(Long userId) {
    return tagRepository.findUsageByUserId(userId);
  }

  @Override
  public Mono<TagUsage> createTag(Long userId, String name, String color, String icon) {
    String trimmed = name.trim();
    return requireNameAvailable(userId, trimmed, null)
        .then(Mono.defer(() -> tagRepository.insertIfAbsent(userId, trimmed)))
        .then(Mono.defer(() -> tagRepository.findByUserIdAndName(userId, trimmed)))
        .switchIfEmpty(Mono.error(alreadyUsed(trimmed)))
        .flatMap(tag -> color == null && icon == null
            ? Mono.just(tag)
            : saveStyle(tag, color, icon))
        .map(tag -> new TagUsage(tag.getId(), tag.getName(), tag.getColor(), tag.getIcon(), 0L));
  }

  @Override
  public Mono<TagUsage> updateTag(Long userId, Long id, String name, String color,
                                  String icon) {
    String trimmed = name.trim();
    return requireTag(userId, id)
        .flatMap(tag -> requireNameAvailable(userId, trimmed, tag.getId()).thenReturn(tag))
        .flatMap(tag -> {
          tag.setName(trimmed);
          applyStyle(tag, color, icon);
          return tagRepository.save(tag);
        })
        .flatMap(this::toUsage);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Se retiran antes los dos enlaces que puede tener: el de las transacciones que lo
   * llevan y el de los movimientos fijos que lo copiarán al confirmarse. Ni unas ni otros
   * desaparecen, solo dejan de estar clasificados por él.</p>
   */
  @Override
  @Transactional
  public Mono<Void> deleteTag(Long userId, Long id) {
    return requireTag(userId, id)
        .flatMap(tag -> transactionTagRepository.deleteByTagId(tag.getId())
            .then(recurringTagRepository.deleteByTagId(tag.getId()))
            .then(tagRepository.delete(tag)));
  }

  /**
   * Guarda el color y el icono de un tag recién dado de alta.
   * El alta en sí no los lleva porque la comparte con los tags que nacen al escribirse dentro
   * de un movimiento, que nunca traen ninguno; quien los elige al crear paga una escritura más.
   *
   * @param tag   tag ya persistido
   * @param color color tal y como llega en la petición, nulo si no se eligió
   * @param icon  icono tal y como llega en la petición, nulo si no se eligió
   * @return el tag con su aspecto guardado
   */
  private Mono<Tag> saveStyle(Tag tag, String color, String icon) {
    applyStyle(tag, color, icon);
    return tagRepository.save(tag);
  }

  /**
   * Aplica el color y el icono recibidos, dejando como está el que no venga.
   *
   * @param tag   tag a modificar
   * @param color color tal y como llega en la petición, nulo para conservarlo
   * @param icon  icono tal y como llega en la petición, nulo para conservarlo
   */
  private void applyStyle(Tag tag, String color, String icon) {
    if (color != null) {
      tag.setColor(ChipStyleRules.toStored(color));
    }
    if (icon != null) {
      tag.setIcon(ChipStyleRules.toStored(icon));
    }
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
        .defaultIfEmpty(
            new TagUsage(tag.getId(), tag.getName(), tag.getColor(), tag.getIcon(), 0L));
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
