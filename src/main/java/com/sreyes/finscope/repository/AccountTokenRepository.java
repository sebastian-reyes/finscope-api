package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.AccountToken;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link AccountToken}.
 * Proporciona operaciones reactivas de acceso a datos sobre la tabla `account_tokens`.
 * Extiende {@link R2dbcRepository} para soporte CRUD.
 */
@Repository
public interface AccountTokenRepository extends R2dbcRepository<AccountToken, Long> {

  /**
   * Busca un enlace por el hash de su valor.
   * No filtra por caducidad ni por consumo: de eso depende el mensaje que se devuelve, y
   * distinguir «caducado» de «inexistente» es justo lo que hace que un enlace expirado
   * pueda pedirse de nuevo en lugar de parecer un error del usuario.
   *
   * @param tokenHash hash del valor recibido del cliente
   * @return el enlace encontrado envuelto en Mono
   */
  Mono<AccountToken> findByTokenHash(String tokenHash);

  /**
   * Borra los enlaces que un usuario tuviera para un propósito.
   * Se llama antes de emitir uno nuevo: pedir otro enlace tiene que dejar sin valor al de
   * antes, o cada petición sumaría una llave más a la misma puerta y la más vieja seguiría
   * abriendo hasta caducar.
   *
   * @param userId  identificador del usuario propietario
   * @param purpose propósito de los enlaces a descartar
   * @return número de enlaces descartados
   */
  @Modifying
  @Query("DELETE FROM account_tokens WHERE user_id = :userId AND purpose = :purpose")
  Mono<Long> deleteByUserIdAndPurpose(Long userId, String purpose);
}
