package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.Tag;
import com.sreyes.finscope.model.query.TransactionTagName;
import java.util.Collection;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link Tag}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `tags`, que es el
 * catálogo de tags de cada usuario. La relación con las transacciones se consulta a través
 * de `transaction_tags`, por lo que las consultas que parten de una transacción o llegan a
 * ella pasan siempre por esa tabla de enlace.
 */
@Repository
public interface TagRepository extends R2dbcRepository<Tag, Long> {

  /**
   * Obtiene en una sola consulta los tags de todas las transacciones indicadas.
   * Devuelve el nombre junto a su transacción para que el ensamblado de la respuesta pueda
   * agrupar sin lanzar una consulta por cada elemento de la página.
   *
   * @param transactionIds identificadores de las transacciones
   * @return flujo reactivo con el nombre de cada tag y la transacción que lo lleva
   */
  @Query("""
      SELECT tt.transaction_id AS transaction_id, t.name_tag AS tag_name
      FROM transaction_tags tt
      INNER JOIN tags t ON t.id_tag = tt.tag_id
      WHERE tt.transaction_id IN (:transactionIds)
      """)
  Flux<TransactionTagName> findNamesByTransactionIdIn(Collection<Long> transactionIds);

  /**
   * Obtiene los nombres distintos de tag que el usuario está usando, en orden alfabético.
   * Alimenta el autocompletado del cliente, que de otro modo no tendría de dónde sacar los
   * tags existentes al no haber catálogo que mantener.
   * La consulta pasa por la tabla de enlace a propósito: un tag que se queda sin
   * transacciones sobrevive en el catálogo, para conservar su grafía si el usuario vuelve a
   * escribirlo, pero deja de sugerirse porque ya no está en uso.
   *
   * @param userId identificador del usuario propietario
   * @return flujo reactivo con los nombres de tag
   */
  @Query("""
      SELECT DISTINCT t.name_tag
      FROM tags t
      INNER JOIN transaction_tags tt ON tt.tag_id = t.id_tag
      WHERE t.user_id = :userId
      ORDER BY t.name_tag
      """)
  Flux<String> findDistinctNamesByUserId(Long userId);

  /**
   * Obtiene los identificadores de las transacciones del usuario que llevan el tag
   * indicado, sin distinguir mayúsculas de minúsculas.
   *
   * @param userId identificador del usuario propietario
   * @param name   nombre del tag
   * @return flujo reactivo con los identificadores de transacción
   */
  @Query("""
      SELECT tt.transaction_id
      FROM transaction_tags tt
      INNER JOIN tags t ON t.id_tag = tt.tag_id
      WHERE t.user_id = :userId AND LOWER(t.name_tag) = LOWER(:name)
      """)
  Flux<Long> findTransactionIdsByUserIdAndName(Long userId, String name);

  /**
   * Obtiene los tags del usuario cuyo nombre, en minúsculas, esté entre los indicados.
   * Resuelve de una vez qué tags de una petición ya existen en el catálogo.
   *
   * @param userId     identificador del usuario propietario
   * @param lowerNames nombres buscados, ya en minúsculas
   * @return flujo reactivo con los tags encontrados
   */
  @Query("""
      SELECT *
      FROM tags
      WHERE user_id = :userId AND LOWER(name_tag) IN (:lowerNames)
      """)
  Flux<Tag> findByUserIdAndLowerNameIn(Long userId, Collection<String> lowerNames);

  /**
   * Da de alta un tag en el catálogo del usuario si todavía no existe.
   * Se apoya en la restricción de unicidad de la base de datos en lugar de comprobar antes
   * si el nombre está libre, porque entre la comprobación y la inserción podría colarse
   * otra petición del mismo usuario creando ese mismo tag.
   *
   * @param userId identificador del usuario propietario
   * @param name   nombre del tag, con la grafía que escribió el usuario
   * @return número de filas insertadas, cero si el tag ya existía
   */
  @Modifying
  @Query("""
      INSERT INTO tags (user_id, name_tag)
      VALUES (:userId, :name)
      ON CONFLICT DO NOTHING
      """)
  Mono<Long> insertIfAbsent(Long userId, String name);
}
