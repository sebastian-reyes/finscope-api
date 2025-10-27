package com.sreyes.finscope.service;

import com.sreyes.finscope.model.entity.Category;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio para la gestión de categorías.
 * Define operaciones reactivas para consultar, guardar y eliminar categorías.
 */
public interface CategoryService {

  /**
   * Obtiene todas las categorías.
   *
   * @return flujo reactivo de categorías
   */
  Flux<Category> findAllCategories();

  /**
   * Busca una categoría por su identificador.
   *
   * @param id identificador de la categoría
   * @return categoría encontrada envuelta en Mono
   */
  Mono<Category> findCategoryById(Long id);

  /**
   * Guarda una nueva categoría.
   *
   * @param category entidad de categoría a guardar
   * @return categoría guardada envuelta en Mono
   */
  Mono<Category> saveCategory(Category category);

  /**
   * Elimina una categoría por su identificador.
   *
   * @param id identificador de la categoría
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteCategoryById(Long id);
}
