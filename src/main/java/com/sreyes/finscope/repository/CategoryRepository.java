package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.query.CategoryUsage;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link Category}.
 * Proporciona operaciones reactivas sobre la tabla `categories`, que es el catálogo de
 * categorías de cada usuario. La relación con las transacciones es de uno a muchos y vive
 * en la propia transacción, de modo que aquí no hay ninguna tabla de enlace que recorrer.
 */
@Repository
public interface CategoryRepository extends R2dbcRepository<Category, Long> {

  /**
   * Obtiene el catálogo completo del usuario junto al uso que le está dando.
   * La unión es externa porque una categoría que todavía no clasifica nada sigue
   * ocupando su nombre y debe poder verse para renombrarla o borrarla. El conteo se
   * resuelve aquí, en una sola consulta, en lugar de preguntarlo categoría a categoría.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con las categorías y su número de transacciones
   */
  @Query("""
      SELECT c.id_category AS category_id,
             c.name_category AS category_name,
             c.applies_to AS category_scope,
             c.is_system AS system_category,
             COUNT(t.id_transaction) AS transaction_count
      FROM categories c
      LEFT JOIN transactions t ON t.category_id = c.id_category
      WHERE c.user_id = :userId
      GROUP BY c.id_category, c.name_category, c.applies_to, c.is_system
      ORDER BY LOWER(c.name_category)
      """)
  Flux<CategoryUsage> findUsageByUserId(Long userId);

  /**
   * Obtiene el uso de una única categoría del usuario.
   * Es la misma proyección que devuelve el listado, para que crear o renombrar responda
   * exactamente con la forma con la que después se lista.
   *
   * @param userId identificador del usuario propietario
   * @param id     identificador de la categoría
   * @return la categoría con su número de transacciones envuelta en Mono
   */
  @Query("""
      SELECT c.id_category AS category_id,
             c.name_category AS category_name,
             c.applies_to AS category_scope,
             c.is_system AS system_category,
             COUNT(t.id_transaction) AS transaction_count
      FROM categories c
      LEFT JOIN transactions t ON t.category_id = c.id_category
      WHERE c.user_id = :userId AND c.id_category = :id
      GROUP BY c.id_category, c.name_category, c.applies_to, c.is_system
      """)
  Mono<CategoryUsage> findUsageByUserIdAndId(Long userId, Long id);

  /**
   * Busca una categoría del usuario por su identificador.
   * Acota por propietario para que una categoría ajena se comporte igual que una
   * inexistente y nadie pueda alcanzarla conociendo su identificador.
   *
   * @param id     identificador de la categoría
   * @param userId identificador del usuario propietario
   * @return la categoría encontrada envuelta en Mono
   */
  Mono<Category> findByIdAndUserId(Long id, Long userId);

  /**
   * Busca la categoría del usuario que se llama como el nombre indicado, sin distinguir
   * mayúsculas de minúsculas.
   *
   * @param userId identificador del usuario propietario
   * @param name   nombre buscado, con cualquier grafía
   * @return la categoría encontrada envuelta en Mono
   */
  @Query("""
      SELECT *
      FROM categories
      WHERE user_id = :userId AND LOWER(name_category) = LOWER(:name)
      """)
  Mono<Category> findByUserIdAndName(Long userId, String name);

  /**
   * Obtiene la categoría de reserva del usuario, la que recibe los movimientos de las
   * categorías que se eliminan.
   *
   * @param userId identificador del usuario propietario
   * @return la categoría de reserva envuelta en Mono
   */
  @Query("SELECT * FROM categories WHERE user_id = :userId AND is_system")
  Mono<Category> findSystemByUserId(Long userId);

  /**
   * Da de alta una categoría en el catálogo del usuario si el nombre todavía está libre.
   * Se apoya en la restricción de unicidad de la base de datos en lugar de comprobar
   * antes si el nombre está libre, porque entre la comprobación y la inserción podría
   * colarse otra petición del mismo usuario creando ese mismo nombre.
   *
   * @param userId    identificador del usuario propietario
   * @param name      nombre de la categoría, con la grafía que escribió el usuario
   * @param appliesTo tipo de movimiento al que se ofrece
   * @param system    si es la categoría de reserva del usuario
   * @return número de filas insertadas, cero si el nombre ya estaba ocupado
   */
  @Modifying
  @Query("""
      INSERT INTO categories (user_id, name_category, applies_to, is_system)
      VALUES (:userId, :name, :appliesTo, :system)
      ON CONFLICT DO NOTHING
      """)
  Mono<Long> insertIfAbsent(Long userId, String name, String appliesTo, boolean system);
}
