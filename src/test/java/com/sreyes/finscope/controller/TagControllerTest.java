package com.sreyes.finscope.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TagService;
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
 * Pruebas del contrato HTTP expuesto por {@link TagController}, que solo ofrece la lectura
 * de los tags que el usuario autenticado ya ha usado.
 */
@WebFluxTest(TagController.class)
@Import(TimeConfig.class)
class TagControllerTest {

  private static final Long USER_ID = 7L;

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
  @DisplayName("Devuelve los tags en uso del usuario autenticado")
  void listsUsedTags() {
    when(tagService.findUsedTagNames(USER_ID)).thenReturn(Flux.just("ocio", "personal"));

    webTestClient.get().uri("/tags")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$[0]").isEqualTo("ocio")
        .jsonPath("$[1]").isEqualTo("personal");

    verify(tagService).findUsedTagNames(USER_ID);
  }

  @Test
  @DisplayName("Devuelve una lista vacía cuando el usuario aún no ha usado ningún tag")
  void returnsEmptyListWhenUserHasNoTags() {
    when(tagService.findUsedTagNames(USER_ID)).thenReturn(Flux.empty());

    webTestClient.get().uri("/tags")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(0);
  }
}
