package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.UserIdentity;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link UserIdentity}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `user_identities`.
 * Extiende {@link R2dbcRepository} para soporte CRUD.
 */
@Repository
public interface UserIdentityRepository extends R2dbcRepository<UserIdentity, Long> {

  /**
   * Busca la identidad de un usuario en el proveedor indicado.
   *
   * @param provider proveedor de la identidad
   * @param subject  identificador del usuario dentro del proveedor
   * @return identidad encontrada envuelta en Mono
   */
  Mono<UserIdentity> findByProviderAndSubject(String provider, String subject);
}
