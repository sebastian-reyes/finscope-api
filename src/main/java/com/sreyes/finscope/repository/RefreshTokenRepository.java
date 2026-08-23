package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.RefreshToken;
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
}
