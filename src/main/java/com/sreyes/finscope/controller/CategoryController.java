package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.CategoriesApi;
import com.sreyes.finscope.api.model.CategoryResponse;
import com.sreyes.finscope.api.model.SaveCategoryRequest;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.CategoryService;
import com.sreyes.finscope.util.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para gestionar el catálogo de categorías del usuario.
 * Implementa el contrato {@link CategoriesApi} generado a partir de la especificación
 * OpenAPI. La categoría es la clasificación principal de una transacción y se comparte
 * entre todas las que clasifica, de modo que renombrarla o eliminarla alcanza a todas;
 * el alcance de cada operación lo resuelve {@link CategoryService} y aquí solo se propaga
 * el usuario autenticado.
 */
@RestController
@RequiredArgsConstructor
public class CategoryController implements CategoriesApi {

  private final CategoryService categoryService;
  private final CategoryMapper categoryMapper;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<Flux<CategoryResponse>>> listCategories(ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMapMany(categoryService::findCategories)
        .map(categoryMapper::toResponse)
        .collectList()
        .map(categories -> ResponseEntity.ok(Flux.fromIterable(categories)));
  }

  @Override
  public Mono<ResponseEntity<CategoryResponse>> createCategory(
      Mono<SaveCategoryRequest> saveCategoryRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(saveCategoryRequest)
        .flatMap(tuple -> categoryService.createCategory(tuple.getT1(), tuple.getT2().getName(),
            tuple.getT2().getAppliesTo()))
        .map(categoryMapper::toResponse)
        .map(category -> ResponseEntity.status(HttpStatus.CREATED).body(category));
  }

  @Override
  public Mono<ResponseEntity<CategoryResponse>> updateCategory(
      Long id, Mono<SaveCategoryRequest> saveCategoryRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(saveCategoryRequest)
        .flatMap(tuple -> categoryService.updateCategory(tuple.getT1(), id,
            tuple.getT2().getName(), tuple.getT2().getAppliesTo()))
        .map(categoryMapper::toResponse)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> deleteCategory(Long id, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> categoryService.deleteCategory(userId, id))
        .thenReturn(ResponseEntity.noContent().build());
  }
}
