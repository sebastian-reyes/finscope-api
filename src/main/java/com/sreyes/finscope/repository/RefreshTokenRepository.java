package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.RefreshToken;
import java.time.LocalDateTime;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link RefreshToken}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `refresh_tokens`.
 * Extiende {@link R2dbcRepository} para soporte CRUD.
 */
@Repository
public interface RefreshTokenRepository extends R2dbcRepository<RefreshToken, Long> {

  /**
   * Busca un token de refresco por el hash de su valor.
   *
   * @param tokenHash hash del token recibido del cliente
   * @return token encontrado envuelto en Mono
   */
  Mono<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Canjea un token vigente: lo revoca y anota cuándo, en una sola sentencia.
   * Es la reclamación atómica de la renovación. Leer el token, comprobar que no esté
   * revocado y guardarlo después eran tres pasos entre los que cabía otra petición con el
   * mismo token; aquí Postgres bloquea la fila, la segunda sentencia espera a la primera y,
   * al volver a evaluar la condición, ya no encuentra nada que actualizar.
   *
   * @param tokenHash hash del token recibido del cliente
   * @param now       momento de la renovación, que marca también la caducidad
   * @return 1 si esta petición se quedó con el token, 0 si no estaba vigente
   */
  @Modifying
  @Query("UPDATE refresh_tokens SET revoked = TRUE, rotated_at = :now "
      + "WHERE token_hash = :tokenHash AND NOT revoked AND expires_at > :now")
  Mono<Long> rotate(String tokenHash, LocalDateTime now);

  /**
   * Anota qué token sustituye a otro y cuándo. La fecha se vuelve a fijar porque, al rehacer
   * una renovación perdida, abre de nuevo el margen de renovación concurrente: una segunda
   * petición con el mismo token que llegue a la vez se atiende como carrera y no como robo.
   *
   * @param id          token rotado
   * @param successorId token emitido en su lugar
   * @param now         momento de la rotación
   * @return número de filas actualizadas
   */
  @Modifying
  @Query("UPDATE refresh_tokens SET replaced_by = :successorId, rotated_at = :now "
      + "WHERE id_refresh_token = :id")
  Mono<Long> markReplaced(Long id, Long successorId, LocalDateTime now);

  /**
   * Anula un sustituto que nunca llegó a usarse, para emitir otro en su lugar.
   * Es la reclamación atómica de una renovación perdida: de dos peticiones que lo intenten a
   * la vez solo una encuentra el sustituto vigente. Le quita además la marca de rotación,
   * como a cualquier token revocado sin rotar, para que no pueda volver a aceptarse.
   *
   * @param successorId token emitido en la renovación cuya respuesta se perdió
   * @return 1 si seguía sin usar y esta petición lo anuló, 0 si ya se había usado o revocado
   */
  @Modifying
  @Query("UPDATE refresh_tokens SET revoked = TRUE, rotated_at = NULL "
      + "WHERE id_refresh_token = :successorId AND NOT revoked")
  Mono<Long> reclaim(Long successorId);

  /**
   * Revoca de una vez todos los tokens vigentes de un usuario.
   * Se usa cuando se presenta un token ya consumido: como cada renovación entrega uno
   * nuevo, ver dos veces el mismo significa que hay una copia en circulación, y sin saber
   * cuál de las dos partes es la legítima lo único seguro es obligar a entrar de nuevo.
   * También al restablecer la contraseña.
   *
   * <p>Borra además la marca de rotación de todos, también de los ya revocados: si no, un
   * token rotado segundos antes seguiría dentro del margen de renovación concurrente y
   * serviría para volver a entrar justo después de haber echado a todo el mundo.</p>
   *
   * @param userId identificador del usuario propietario
   * @return número de tokens revocados
   */
  @Modifying
  @Query("UPDATE refresh_tokens SET revoked = TRUE, rotated_at = NULL "
      + "WHERE user_id = :userId AND (NOT revoked OR rotated_at IS NOT NULL)")
  Mono<Long> revokeAllByUserId(Long userId);
}
