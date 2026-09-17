package com.sreyes.finscope.service;

import com.sreyes.finscope.api.model.CategoryScope;
import com.sreyes.finscope.model.query.CategoryUsage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Servicio para la gestión del catálogo de categorías del usuario.
 * Toda operación parte del identificador del propietario, de modo que una categoría ajena
 * se comporta igual que una inexistente.
 */
public interface CategoryService {

  /**
   * Obtiene el catálogo completo del usuario junto al uso de cada categoría.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con las categorías y su número de transacciones
   */
  Flux<CategoryUsage> findCategories(Long userId);

  /**
   * Crea una categoría en el catálogo del usuario.
   *
   * @param userId    identificador del usuario propietario
   * @param name      nombre de la categoría
   * @param appliesTo tipo de movimiento al que se ofrece, egresos si no se indica
   * @param color     color de su ficha tal y como llega en la petición, nulo para el
   *                  automático
   * @param icon      icono de su ficha tal y como llega en la petición, nulo para el
   *                  automático
   * @return la categoría creada, todavía sin transacciones
   */
  Mono<CategoryUsage> createCategory(Long userId, String name, CategoryScope appliesTo,
                                     String color, String icon);

  /**
   * Actualiza el nombre, el ámbito, el color o el icono de una categoría del usuario.
   * Los valores no informados se dejan como están.
   *
   * @param userId    identificador del usuario propietario
   * @param id        identificador de la categoría
   * @param name      nombre nuevo
   * @param appliesTo ámbito nuevo, o nulo para conservar el actual
   * @param color     color nuevo tal y como llega en la petición, o nulo para conservar el
   *                  actual
   * @param icon      icono nuevo tal y como llega en la petición, o nulo para conservar el
   *                  actual
   * @return la categoría actualizada
   */
  Mono<CategoryUsage> updateCategory(Long userId, Long id, String name, CategoryScope appliesTo,
                                     String color, String icon);

  /**
   * Elimina una categoría del catálogo reasignando sus transacciones a la categoría de
   * reserva del usuario.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la categoría
   * @return Mono vacío al completar la eliminación
   */
  Mono<Void> deleteCategory(Long userId, Long id);

  /**
   * Siembra el catálogo inicial de una cuenta recién creada.
   *
   * @param userId identificador del usuario propietario
   * @return Mono vacío al completar la siembra
   */
  Mono<Void> seedDefaults(Long userId);
}
