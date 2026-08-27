package com.sreyes.finscope.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.exception.custom.TagNameAlreadyUsedException;
import com.sreyes.finscope.exception.custom.TagNotFoundException;
import com.sreyes.finscope.model.query.TagUsage;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TagService;
import com.sreyes.finscope.util.mapper.TagMapperImpl;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Pruebas del contrato HTTP expuesto por {@link TagController}: el catálogo con su uso y las
 * tres operaciones de mantenimiento. El mapper real se importa en lugar de simularse, porque
 * parte de lo que se comprueba aquí es la forma del JSON que sale de él.
 */
@WebFluxTest(TagController.class)
@Import({TimeConfig.class, TagMapperImpl.class})
class TagControllerTest {

  private static final Long USER_ID = 7L;
  private static final Long TAG_ID = 3L;

  @Autowired
  private WebTestClient webTestClient;

  @MockitoBean
  private TagService tagService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutateWith(mockUser()).mutateWith(csrf());
    when(authenticatedUser.currentUserId()).thenReturn(Mono.just(USER_ID));
  }

  @Test
  @DisplayName("Devuelve el catálogo del usuario con el uso de cada tag")
  void listsTagsWithUsage() {
    when(tagService.findTags(USER_ID)).thenReturn(Flux.just(
        new TagUsage(1L, "ocio", 4L),
        new TagUsage(2L, "personal", 0L)));

    webTestClient.get().uri("/tags")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$[0].id").isEqualTo(1)
        .jsonPath("$[0].name").isEqualTo("ocio")
        .jsonPath("$[0].transactionCount").isEqualTo(4)
        .jsonPath("$[1].transactionCount").isEqualTo(0);

    verify(tagService).findTags(USER_ID);
  }

  @Test
  @DisplayName("Devuelve una lista vacía cuando el catálogo del usuario está vacío")
  void returnsEmptyListWhenUserHasNoTags() {
    when(tagService.findTags(USER_ID)).thenReturn(Flux.empty());

    webTestClient.get().uri("/tags")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(0);
  }

  @Test
  @DisplayName("Crea un tag y responde 201 con el tag sin uso")
  void createsTag() {
    when(tagService.createTag(USER_ID, "ocio"))
        .thenReturn(Mono.just(new TagUsage(TAG_ID, "ocio", 0L)));

    webTestClient.post().uri("/tags")
        .bodyValue(Map.of("name", "ocio"))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo(TAG_ID)
        .jsonPath("$.name").isEqualTo("ocio")
        .jsonPath("$.transactionCount").isEqualTo(0);
  }

  @Test
  @DisplayName("Traduce a 409 el alta de un nombre que el usuario ya tiene")
  void reportsConflictOnDuplicateName() {
    when(tagService.createTag(USER_ID, "ocio"))
        .thenReturn(Mono.error(new TagNameAlreadyUsedException("A tag named ocio already exists")));

    webTestClient.post().uri("/tags")
        .bodyValue(Map.of("name", "ocio"))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.code").isEqualTo("TAG_NAME_ALREADY_USED");
  }

  @Test
  @DisplayName("Rechaza un nombre en blanco antes de llegar al servicio")
  void rejectsBlankName() {
    webTestClient.post().uri("/tags")
        .bodyValue(Map.of("name", ""))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Renombra un tag y devuelve su uso actual")
  void renamesTag() {
    when(tagService.renameTag(USER_ID, TAG_ID, "tiempo libre"))
        .thenReturn(Mono.just(new TagUsage(TAG_ID, "tiempo libre", 4L)));

    webTestClient.patch().uri("/tags/{id}", TAG_ID)
        .bodyValue(Map.of("name", "tiempo libre"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.name").isEqualTo("tiempo libre")
        .jsonPath("$.transactionCount").isEqualTo(4);
  }

  @Test
  @DisplayName("Traduce a 404 el tag que no está en el catálogo del usuario")
  void reportsNotFoundOnUnknownTag() {
    when(tagService.renameTag(USER_ID, TAG_ID, "ocio"))
        .thenReturn(Mono.error(new TagNotFoundException("Tag not found with id: " + TAG_ID)));

    webTestClient.patch().uri("/tags/{id}", TAG_ID)
        .bodyValue(Map.of("name", "ocio"))
        .exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.code").isEqualTo("TAG_NOT_FOUND");
  }

  @Test
  @DisplayName("Elimina un tag y responde 204 sin cuerpo")
  void deletesTag() {
    when(tagService.deleteTag(USER_ID, TAG_ID)).thenReturn(Mono.empty());

    webTestClient.delete().uri("/tags/{id}", TAG_ID)
        .exchange()
        .expectStatus().isNoContent()
        .expectBody().isEmpty();

    verify(tagService).deleteTag(USER_ID, TAG_ID);
  }
}
