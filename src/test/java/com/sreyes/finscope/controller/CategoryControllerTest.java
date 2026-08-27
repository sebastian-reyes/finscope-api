package com.sreyes.finscope.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.config.TimeConfig;
import com.sreyes.finscope.exception.custom.CategoryNameAlreadyUsedException;
import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.exception.custom.SystemCategoryException;
import com.sreyes.finscope.model.query.CategoryUsage;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.CategoryService;
import com.sreyes.finscope.util.mapper.CategoryMapperImpl;
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
 * Pruebas del contrato HTTP expuesto por {@link CategoryController}: el catálogo con su uso
 * y las tres operaciones de mantenimiento. El mapper real se importa en lugar de simularse,
 * porque parte de lo que se comprueba aquí es la forma del JSON que sale de él.
 */
@WebFluxTest(CategoryController.class)
@Import({TimeConfig.class, CategoryMapperImpl.class})
class CategoryControllerTest {

  private static final Long USER_ID = 7L;
  private static final Long CATEGORY_ID = 4L;

  @Autowired
  private WebTestClient webTestClient;

  @MockitoBean
  private CategoryService categoryService;

  @MockitoBean
  private AuthenticatedUser authenticatedUser;

  @BeforeEach
  void setUp() {
    webTestClient = webTestClient.mutateWith(mockUser()).mutateWith(csrf());
    when(authenticatedUser.currentUserId()).thenReturn(Mono.just(USER_ID));
  }

  @Test
  @DisplayName("Devuelve el catálogo del usuario con el uso de cada categoría")
  void listsCategoriesWithUsage() {
    when(categoryService.findCategories(USER_ID)).thenReturn(Flux.just(
        new CategoryUsage(CATEGORY_ID, "Comida", "EXPENSE", false, 12L),
        new CategoryUsage(1L, "Otros", "BOTH", true, 3L)));

    webTestClient.get().uri("/categories")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$[0].id").isEqualTo(4)
        .jsonPath("$[0].name").isEqualTo("Comida")
        .jsonPath("$[0].appliesTo").isEqualTo("EXPENSE")
        .jsonPath("$[0].isSystem").isEqualTo(false)
        .jsonPath("$[0].transactionCount").isEqualTo(12)
        .jsonPath("$[1].appliesTo").isEqualTo("BOTH")
        .jsonPath("$[1].isSystem").isEqualTo(true);

    verify(categoryService).findCategories(USER_ID);
  }

  @Test
  @DisplayName("Crea una categoría y responde 201 sin uso")
  void createsCategory() {
    when(categoryService.createCategory(eq(USER_ID), eq("Mascotas"), any()))
        .thenReturn(Mono.just(new CategoryUsage(9L, "Mascotas", "EXPENSE", false, 0L)));

    webTestClient.post().uri("/categories")
        .bodyValue(Map.of("name", "Mascotas", "appliesTo", "EXPENSE"))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo(9)
        .jsonPath("$.name").isEqualTo("Mascotas")
        .jsonPath("$.transactionCount").isEqualTo(0);
  }

  @Test
  @DisplayName("Toma egresos como ámbito por defecto al no informarlo")
  void defaultsScopeToExpense() {
    when(categoryService.createCategory(USER_ID, "Mascotas", null))
        .thenReturn(Mono.just(new CategoryUsage(9L, "Mascotas", "EXPENSE", false, 0L)));

    webTestClient.post().uri("/categories")
        .bodyValue(Map.of("name", "Mascotas"))
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.appliesTo").isEqualTo("EXPENSE");
  }

  @Test
  @DisplayName("Rechaza con 400 una categoría sin nombre")
  void rejectsCategoryWithoutName() {
    webTestClient.post().uri("/categories")
        .bodyValue(Map.of("appliesTo", "EXPENSE"))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @DisplayName("Traduce en 409 el nombre de categoría ya ocupado")
  void translatesDuplicatedName() {
    when(categoryService.createCategory(eq(USER_ID), eq("Comida"), any()))
        .thenReturn(Mono.error(
            new CategoryNameAlreadyUsedException("A category named Comida already exists")));

    webTestClient.post().uri("/categories")
        .bodyValue(Map.of("name", "Comida"))
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.code").isEqualTo("CATEGORY_NAME_ALREADY_USED");
  }

  @Test
  @DisplayName("Actualiza el nombre y el ámbito de una categoría")
  void updatesCategory() {
    when(categoryService.updateCategory(USER_ID, CATEGORY_ID, "Alimentación",
        CategoryScope.BOTH))
        .thenReturn(Mono.just(new CategoryUsage(CATEGORY_ID, "Alimentación", "BOTH", false, 12L)));

    webTestClient.patch().uri("/categories/{id}", CATEGORY_ID)
        .bodyValue(Map.of("name", "Alimentación", "appliesTo", "BOTH"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.name").isEqualTo("Alimentación")
        .jsonPath("$.appliesTo").isEqualTo("BOTH")
        .jsonPath("$.transactionCount").isEqualTo(12);
  }

  @Test
  @DisplayName("Traduce en 404 la categoría inexistente")
  void translatesNotFound() {
    when(categoryService.updateCategory(eq(USER_ID), eq(99L), any(), any()))
        .thenReturn(Mono.error(new CategoryNotFoundException("Category not found with id: 99")));

    webTestClient.patch().uri("/categories/99")
        .bodyValue(Map.of("name", "Comida"))
        .exchange()
        .expectStatus().isNotFound()
        .expectBody()
        .jsonPath("$.code").isEqualTo("CATEGORY_NOT_FOUND");
  }

  @Test
  @DisplayName("Elimina una categoría y responde 204")
  void deletesCategory() {
    when(categoryService.deleteCategory(USER_ID, CATEGORY_ID)).thenReturn(Mono.empty());

    webTestClient.delete().uri("/categories/{id}", CATEGORY_ID)
        .exchange()
        .expectStatus().isNoContent();

    verify(categoryService).deleteCategory(USER_ID, CATEGORY_ID);
  }

  @Test
  @DisplayName("Traduce en 409 el intento de eliminar la categoría de reserva")
  void translatesProtectedCategory() {
    when(categoryService.deleteCategory(USER_ID, 1L))
        .thenReturn(Mono.error(new SystemCategoryException("The fallback category cannot "
            + "be deleted")));

    webTestClient.delete().uri("/categories/1")
        .exchange()
        .expectStatus().isEqualTo(409)
        .expectBody()
        .jsonPath("$.code").isEqualTo("SYSTEM_CATEGORY_PROTECTED");
  }
}
