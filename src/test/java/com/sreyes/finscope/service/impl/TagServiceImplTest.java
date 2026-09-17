package com.sreyes.finscope.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.exception.custom.TagNameAlreadyUsedException;
import com.sreyes.finscope.exception.custom.TagNotFoundException;
import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.model.query.TagUsage;
import com.sreyes.finscope.repository.RecurringTagRepository;
import com.sreyes.finscope.repository.TagRepository;
import com.sreyes.finscope.repository.TransactionTagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link TagServiceImpl}.
 * Se centran en las dos reglas que sostienen el catálogo: toda operación queda acotada al
 * usuario que la pide, de modo que un tag ajeno se comporta igual que uno inexistente, y un
 * nombre repetido se rechaza en lugar de fusionar dos tags.
 */
@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final Long OTHER_USER_ID = 8L;
  private static final Long TAG_ID = 3L;

  @Mock
  private TagRepository tagRepository;

  @Mock
  private TransactionTagRepository transactionTagRepository;

  @Mock
  private RecurringTagRepository recurringTagRepository;

  @InjectMocks
  private TagServiceImpl tagService;

  /**
   * Construye un tag ya persistido del usuario de las pruebas.
   *
   * @param name nombre del tag
   * @return la entidad de tag
   */
  private Tag tag(String name) {
    return new Tag(TAG_ID, USER_ID, name, null, null);
  }

  @Test
  @DisplayName("Devuelve el catálogo del usuario con el uso de cada tag")
  void returnsCatalogueWithUsage() {
    when(tagRepository.findUsageByUserId(USER_ID)).thenReturn(Flux.just(
        new TagUsage(1L, "ocio", null, null, 4L),
        new TagUsage(2L, "personal", null, null, 0L)));

    StepVerifier.create(tagService.findTags(USER_ID))
        .expectNext(new TagUsage(1L, "ocio", null, null, 4L))
        .expectNext(new TagUsage(2L, "personal", null, null, 0L))
        .verifyComplete();
  }

  @Test
  @DisplayName("No mezcla los tags de otro usuario")
  void doesNotLeakAnotherUsersTags() {
    when(tagRepository.findUsageByUserId(OTHER_USER_ID))
        .thenReturn(Flux.just(new TagUsage(9L, "trabajo", null, null, 1L)));

    StepVerifier.create(tagService.findTags(OTHER_USER_ID))
        .expectNext(new TagUsage(9L, "trabajo", null, null, 1L))
        .verifyComplete();

    verify(tagRepository).findUsageByUserId(OTHER_USER_ID);
  }

  @Test
  @DisplayName("Crea el tag recortado y lo devuelve sin uso")
  void createsTagWithoutUsage() {
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio"))
        .thenReturn(Mono.empty(), Mono.just(tag("ocio")));
    when(tagRepository.insertIfAbsent(USER_ID, "ocio")).thenReturn(Mono.just(1L));

    StepVerifier.create(tagService.createTag(USER_ID, "  ocio  ", null, null))
        .expectNext(new TagUsage(TAG_ID, "ocio", null, null, 0L))
        .verifyComplete();

    verify(tagRepository).insertIfAbsent(USER_ID, "ocio");
  }

  @Test
  @DisplayName("Rechaza crear un tag cuyo nombre ya tiene el usuario")
  void rejectsDuplicateNameOnCreate() {
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio")).thenReturn(Mono.just(tag("ocio")));

    StepVerifier.create(tagService.createTag(USER_ID, "ocio", null, null))
        .expectError(TagNameAlreadyUsedException.class)
        .verify();

    verify(tagRepository, never()).insertIfAbsent(USER_ID, "ocio");
  }

  @Test
  @DisplayName("Renombra el tag y devuelve su uso actual")
  void renamesTag() {
    Tag existing = tag("ocio");
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(existing));
    when(tagRepository.findByUserIdAndName(USER_ID, "tiempo libre")).thenReturn(Mono.empty());
    when(tagRepository.save(any(Tag.class))).thenReturn(Mono.just(tag("tiempo libre")));
    when(tagRepository.findUsageByUserId(USER_ID))
        .thenReturn(Flux.just(new TagUsage(TAG_ID, "tiempo libre", null, null, 4L)));

    StepVerifier.create(tagService.updateTag(USER_ID, TAG_ID, "tiempo libre", null, null))
        .expectNext(new TagUsage(TAG_ID, "tiempo libre", null, null, 4L))
        .verifyComplete();
  }

  @Test
  @DisplayName("Conservar el propio nombre no cuenta como conflicto")
  void keepingOwnNameIsNotAConflict() {
    Tag existing = tag("ocio");
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(existing));
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio")).thenReturn(Mono.just(existing));
    when(tagRepository.save(any(Tag.class))).thenReturn(Mono.just(existing));
    when(tagRepository.findUsageByUserId(USER_ID))
        .thenReturn(Flux.just(new TagUsage(TAG_ID, "ocio", null, null, 4L)));

    StepVerifier.create(tagService.updateTag(USER_ID, TAG_ID, "ocio", null, null))
        .expectNext(new TagUsage(TAG_ID, "ocio", null, null, 4L))
        .verifyComplete();
  }

  @Test
  @DisplayName("Rechaza renombrar a un nombre que ya ocupa otro tag")
  void rejectsDuplicateNameOnRename() {
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(tag("ocio")));
    when(tagRepository.findByUserIdAndName(USER_ID, "personal"))
        .thenReturn(Mono.just(new Tag(99L, USER_ID, "personal", null, null)));

    StepVerifier.create(tagService.updateTag(USER_ID, TAG_ID, "personal", null, null))
        .expectError(TagNameAlreadyUsedException.class)
        .verify();

    verify(tagRepository, never()).save(any(Tag.class));
  }

  @Test
  @DisplayName("Falla al renombrar un tag que no está en el catálogo del usuario")
  void failsRenamingUnknownTag() {
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(tagService.updateTag(USER_ID, TAG_ID, "ocio", null, null))
        .expectError(TagNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Al borrar un tag lo retira de las transacciones y de los fijos que lo llevan")
  void deletesTagAndItsRelations() {
    Tag existing = tag("ocio");
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(existing));
    when(transactionTagRepository.deleteByTagId(TAG_ID)).thenReturn(Mono.empty());
    when(recurringTagRepository.deleteByTagId(TAG_ID)).thenReturn(Mono.empty());
    when(tagRepository.delete(existing)).thenReturn(Mono.empty());

    StepVerifier.create(tagService.deleteTag(USER_ID, TAG_ID)).verifyComplete();

    verify(transactionTagRepository).deleteByTagId(TAG_ID);
    // Un fijo que lo llevaba sobrevive: solo deja de copiarlo al confirmar cada mes.
    verify(recurringTagRepository).deleteByTagId(TAG_ID);
    verify(tagRepository).delete(existing);
  }

  @Test
  @DisplayName("Falla al borrar un tag que no está en el catálogo del usuario")
  void failsDeletingUnknownTag() {
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(tagService.deleteTag(USER_ID, TAG_ID))
        .expectError(TagNotFoundException.class)
        .verify();

    verify(transactionTagRepository, never()).deleteByTagId(TAG_ID);
  }

  @Test
  @DisplayName("Al crear con color lo guarda en minúsculas")
  void createsTagWithColor() {
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio"))
        .thenReturn(Mono.empty(), Mono.just(tag("ocio")));
    when(tagRepository.insertIfAbsent(USER_ID, "ocio")).thenReturn(Mono.just(1L));
    when(tagRepository.save(any(Tag.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    StepVerifier.create(tagService.createTag(USER_ID, "ocio", "#E8A33D", null))
        .expectNext(new TagUsage(TAG_ID, "ocio", "#e8a33d", null, 0L))
        .verifyComplete();
  }

  @Test
  @DisplayName("Al crear sin color no escribe nada más que el alta")
  void createsTagWithoutColorInASingleWrite() {
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio"))
        .thenReturn(Mono.empty(), Mono.just(tag("ocio")));
    when(tagRepository.insertIfAbsent(USER_ID, "ocio")).thenReturn(Mono.just(1L));

    StepVerifier.create(tagService.createTag(USER_ID, "ocio", null, null))
        .expectNext(new TagUsage(TAG_ID, "ocio", null, null, 0L))
        .verifyComplete();

    verify(tagRepository, never()).save(any(Tag.class));
  }

  @Test
  @DisplayName("Cambiar solo el nombre conserva el color elegido")
  void renamingKeepsColor() {
    Tag existing = new Tag(TAG_ID, USER_ID, "ocio", "preset-3", null);
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(existing));
    when(tagRepository.findByUserIdAndName(USER_ID, "viajes")).thenReturn(Mono.empty());
    when(tagRepository.save(any(Tag.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(tagRepository.findUsageByUserId(USER_ID)).thenReturn(Flux.empty());

    StepVerifier.create(tagService.updateTag(USER_ID, TAG_ID, "viajes", null, null))
        .expectNext(new TagUsage(TAG_ID, "viajes", "preset-3", null, 0L))
        .verifyComplete();
  }

  @Test
  @DisplayName("El color auto devuelve el tag al color deducido del nombre")
  void autoClearsColor() {
    Tag existing = new Tag(TAG_ID, USER_ID, "ocio", "#112233", null);
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(existing));
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio")).thenReturn(Mono.just(existing));
    when(tagRepository.save(any(Tag.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(tagRepository.findUsageByUserId(USER_ID)).thenReturn(Flux.empty());

    StepVerifier.create(tagService.updateTag(USER_ID, TAG_ID, "ocio", "auto", null))
        .expectNext(new TagUsage(TAG_ID, "ocio", null, null, 0L))
        .verifyComplete();
  }

  @Test
  @DisplayName("Al crear con icono lo guarda junto al color en una sola escritura extra")
  void createsTagWithIcon() {
    when(tagRepository.findByUserIdAndName(USER_ID, "viaje"))
        .thenReturn(Mono.empty(), Mono.just(tag("viaje")));
    when(tagRepository.insertIfAbsent(USER_ID, "viaje")).thenReturn(Mono.just(1L));
    when(tagRepository.save(any(Tag.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    StepVerifier.create(tagService.createTag(USER_ID, "viaje", null, "Airplane"))
        .expectNext(new TagUsage(TAG_ID, "viaje", null, "airplane", 0L))
        .verifyComplete();

    verify(tagRepository, times(1)).save(any(Tag.class));
  }

  @Test
  @DisplayName("Cambiar el icono conserva el color, y auto lo quita")
  void updatesIconIndependentlyOfColor() {
    Tag existing = new Tag(TAG_ID, USER_ID, "ocio", "preset-1", "controller");
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(existing));
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio")).thenReturn(Mono.just(existing));
    when(tagRepository.save(any(Tag.class)))
        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    when(tagRepository.findUsageByUserId(USER_ID)).thenReturn(Flux.empty());

    StepVerifier.create(tagService.updateTag(USER_ID, TAG_ID, "ocio", null, "auto"))
        .expectNext(new TagUsage(TAG_ID, "ocio", "preset-1", null, 0L))
        .verifyComplete();
  }
}
