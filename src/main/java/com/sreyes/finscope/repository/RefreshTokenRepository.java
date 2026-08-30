package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.RefreshToken;
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
   * Revoca de una vez todos los tokens vigentes de un usuario.
   * Se usa cuando se presenta un token ya consumido: como cada renovación entrega uno
   * nuevo, ver dos veces el mismo significa que hay una copia en circulación, y sin saber
   * cuál de las dos partes es la legítima lo único seguro es obligar a entrar de nuevo.
   *
   * @param userId identificador del usuario propietario
   * @return número de tokens revocados
   */
  @Modifying
  @Query("UPDATE refresh_tokens SET revoked = TRUE WHERE user_id = :userId AND NOT revoked")
  Mono<Long> revokeAllByUserId(Long userId);
}
