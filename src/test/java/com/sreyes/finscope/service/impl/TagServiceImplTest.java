package com.sreyes.finscope.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreyes.finscope.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Pruebas unitarias de {@link TagServiceImpl}, centradas en que la consulta de tags en uso
 * quede acotada al usuario que la pide.
 */
@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

  private static final Long USER_ID = 7L;
  private static final Long OTHER_USER_ID = 8L;

  @Mock
  private TagRepository tagRepository;

  @InjectMocks
  private TagServiceImpl tagService;

  @Test
  @DisplayName("Devuelve los tags que el usuario ya ha usado")
  void returnsUsedTagNames() {
    when(tagRepository.findDistinctNamesByUserId(USER_ID))
        .thenReturn(Flux.just("ocio", "personal"));

    StepVerifier.create(tagService.findUsedTagNames(USER_ID))
        .expectNext("ocio", "personal")
        .verifyComplete();
  }

  @Test
  @DisplayName("No devuelve nada cuando el usuario aún no ha usado ningún tag")
  void returnsNothingWhenUserHasNoTags() {
    when(tagRepository.findDistinctNamesByUserId(USER_ID)).thenReturn(Flux.empty());

    StepVerifier.create(tagService.findUsedTagNames(USER_ID)).verifyComplete();
  }

  @Test
  @DisplayName("No mezcla los tags de otro usuario")
  void doesNotLeakAnotherUsersTags() {
    when(tagRepository.findDistinctNamesByUserId(OTHER_USER_ID)).thenReturn(Flux.just("trabajo"));

    StepVerifier.create(tagService.findUsedTagNames(OTHER_USER_ID))
        .expectNext("trabajo")
        .verifyComplete();

    verify(tagRepository).findDistinctNamesByUserId(OTHER_USER_ID);
  }
}
