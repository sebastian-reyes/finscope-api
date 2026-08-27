package com.sreyes.finscope.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.exception.custom.TagNameAlreadyUsedException;
import com.sreyes.finscope.exception.custom.TagNotFoundException;
import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.model.query.TagUsage;
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

  @InjectMocks
  private TagServiceImpl tagService;

  /**
   * Construye un tag ya persistido del usuario de las pruebas.
   *
   * @param name nombre del tag
   * @return la entidad de tag
   */
  private Tag tag(String name) {
    return new Tag(TAG_ID, USER_ID, name);
  }

  @Test
  @DisplayName("Devuelve el catálogo del usuario con el uso de cada tag")
  void returnsCatalogueWithUsage() {
    when(tagRepository.findUsageByUserId(USER_ID)).thenReturn(Flux.just(
        new TagUsage(1L, "ocio", 4L),
        new TagUsage(2L, "personal", 0L)));

    StepVerifier.create(tagService.findTags(USER_ID))
        .expectNext(new TagUsage(1L, "ocio", 4L))
        .expectNext(new TagUsage(2L, "personal", 0L))
        .verifyComplete();
  }

  @Test
  @DisplayName("No mezcla los tags de otro usuario")
  void doesNotLeakAnotherUsersTags() {
    when(tagRepository.findUsageByUserId(OTHER_USER_ID))
        .thenReturn(Flux.just(new TagUsage(9L, "trabajo", 1L)));

    StepVerifier.create(tagService.findTags(OTHER_USER_ID))
        .expectNext(new TagUsage(9L, "trabajo", 1L))
        .verifyComplete();

    verify(tagRepository).findUsageByUserId(OTHER_USER_ID);
  }

  @Test
  @DisplayName("Crea el tag recortado y lo devuelve sin uso")
  void createsTagWithoutUsage() {
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio"))
        .thenReturn(Mono.empty(), Mono.just(tag("ocio")));
    when(tagRepository.insertIfAbsent(USER_ID, "ocio")).thenReturn(Mono.just(1L));

    StepVerifier.create(tagService.createTag(USER_ID, "  ocio  "))
        .expectNext(new TagUsage(TAG_ID, "ocio", 0L))
        .verifyComplete();

    verify(tagRepository).insertIfAbsent(USER_ID, "ocio");
  }

  @Test
  @DisplayName("Rechaza crear un tag cuyo nombre ya tiene el usuario")
  void rejectsDuplicateNameOnCreate() {
    when(tagRepository.findByUserIdAndName(USER_ID, "ocio")).thenReturn(Mono.just(tag("ocio")));

    StepVerifier.create(tagService.createTag(USER_ID, "ocio"))
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
        .thenReturn(Flux.just(new TagUsage(TAG_ID, "tiempo libre", 4L)));

    StepVerifier.create(tagService.renameTag(USER_ID, TAG_ID, "tiempo libre"))
        .expectNext(new TagUsage(TAG_ID, "tiempo libre", 4L))
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
        .thenReturn(Flux.just(new TagUsage(TAG_ID, "ocio", 4L)));

    StepVerifier.create(tagService.renameTag(USER_ID, TAG_ID, "ocio"))
        .expectNext(new TagUsage(TAG_ID, "ocio", 4L))
        .verifyComplete();
  }

  @Test
  @DisplayName("Rechaza renombrar a un nombre que ya ocupa otro tag")
  void rejectsDuplicateNameOnRename() {
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(tag("ocio")));
    when(tagRepository.findByUserIdAndName(USER_ID, "personal"))
        .thenReturn(Mono.just(new Tag(99L, USER_ID, "personal")));

    StepVerifier.create(tagService.renameTag(USER_ID, TAG_ID, "personal"))
        .expectError(TagNameAlreadyUsedException.class)
        .verify();

    verify(tagRepository, never()).save(any(Tag.class));
  }

  @Test
  @DisplayName("Falla al renombrar un tag que no está en el catálogo del usuario")
  void failsRenamingUnknownTag() {
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(tagService.renameTag(USER_ID, TAG_ID, "ocio"))
        .expectError(TagNotFoundException.class)
        .verify();
  }

  @Test
  @DisplayName("Al borrar un tag lo retira antes de las transacciones que lo llevan")
  void deletesTagAndItsRelations() {
    Tag existing = tag("ocio");
    when(tagRepository.findByIdAndUserId(TAG_ID, USER_ID)).thenReturn(Mono.just(existing));
    when(transactionTagRepository.deleteByTagId(TAG_ID)).thenReturn(Mono.empty());
    when(tagRepository.delete(existing)).thenReturn(Mono.empty());

    StepVerifier.create(tagService.deleteTag(USER_ID, TAG_ID)).verifyComplete();

    verify(transactionTagRepository).deleteByTagId(TAG_ID);
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
}
