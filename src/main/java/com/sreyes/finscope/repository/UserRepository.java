package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link User}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `users`.
 * Extiende {@link R2dbcRepository} para soporte CRUD.
 */
@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {

  /**
   * Busca un usuario por su correo sin distinguir mayúsculas.
   * La comparación replica la del índice único de la tabla para poder aprovecharlo.
   *
   * @param email correo del usuario
   * @return usuario encontrado envuelto en Mono
   */
  @Query("SELECT * FROM users WHERE lower(email) = lower(:email)")
  Mono<User> findByEmailIgnoreCase(String email);
}
