package com.sreyes.finscope.controller;

import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para gestionar las operaciones relacionadas con las categorías.
 * Proporciona endpoints para consultar, crear y eliminar categorías.
 */
@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
public class CategoryController {

  private final CategoryService categoryService;

  /**
   * Obtiene todas las categorías.
   *
   * @return un {@link Flux} de {@link Category} con todas las categorías registradas.
   */
  @GetMapping
  @ResponseStatus(HttpStatus.OK)
  public Flux<Category> getAllCategories() {
    return categoryService.findAllCategories();
  }

  /**
   * Obtiene una categoría por su identificador.
   *
   * @param id el identificador de la categoría.
   * @return un {@link Mono} de {@link Category} si existe, vacío en caso contrario.
   */
  @GetMapping("/{id}")
  @ResponseStatus(HttpStatus.OK)
  public Mono<Category> getCategoryById(@PathVariable Long id) {
    return categoryService.findCategoryById(id);
  }

  /**
   * Crea una nueva categoría.
   *
   * @param category la categoría a crear.
   * @return un {@link Mono} de {@link Category} con la categoría creada.
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<Category> createCategory(@RequestBody Category category) {
    return categoryService.saveCategory(category);
  }

  /**
   * Elimina una categoría por su identificador.
   *
   * @param id el identificador de la categoría a eliminar.
   * @return un {@link Mono} vacío cuando la eliminación se completa.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> deleteCategory(@PathVariable Long id) {
    return categoryService.deleteCategoryById(id);
  }

}
