package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.entity.PushSubscription;
import java.time.LocalDateTime;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repositorio para la entidad {@link PushSubscription}.
 * Proporciona operaciones reactivas sobre la tabla `push_subscriptions`.
 */
@Repository
public interface PushSubscriptionRepository extends R2dbcRepository<PushSubscription, Long> {

  /**
   * Guarda la suscripción de un navegador, o la actualiza si ya existía.
   *
   * <p>La dirección es única y manda sobre el usuario: si en el mismo teléfono entra otra
   * cuenta y activa los avisos, la fila pasa a ser suya. Lo contrario sería que la persona
   * anterior siguiera recibiendo en un teléfono que ya no usa los avisos de su cuenta, o que
   * la nueva recibiera los de las dos.</p>
   *
   * <p>Un navegador puede renovar sus claves sin cambiar de dirección, así que también se
   * sobrescriben. La fecha de alta solo se reinicia cuando cambia de dueño.</p>
   *
   * @param userId   identificador del usuario que se suscribe
   * @param endpoint dirección del servicio de push
   * @param p256dh   clave pública del navegador
   * @param auth     secreto de autenticación del navegador
   * @return número de filas insertadas o actualizadas
   */
  @Modifying
  @Query("""
      INSERT INTO push_subscriptions (user_id, endpoint, p256dh, auth)
      VALUES (:userId, :endpoint, :p256dh, :auth)
      ON CONFLICT (endpoint) DO UPDATE
      SET created_at = CASE WHEN push_subscriptions.user_id = EXCLUDED.user_id
                            THEN push_subscriptions.created_at
                            ELSE CURRENT_TIMESTAMP END,
          user_id = EXCLUDED.user_id,
          p256dh = EXCLUDED.p256dh,
          auth = EXCLUDED.auth
      """)
  Mono<Long> upsert(Long userId, String endpoint, String p256dh, String auth);

  /**
   * Da de baja la suscripción de un navegador, solo si es del usuario que la pide.
   *
   * @param userId   identificador del usuario
   * @param endpoint dirección de la suscripción
   * @return número de filas borradas, cero si no existía o era de otra cuenta
   */
  @Modifying
  @Query("DELETE FROM push_subscriptions WHERE user_id = :userId AND endpoint = :endpoint")
  Mono<Long> deleteByUserIdAndEndpoint(Long userId, String endpoint);

  /**
   * Obtiene los dispositivos suscritos de un usuario.
   *
   * @param userId identificador del usuario
   * @return flujo reactivo con sus suscripciones
   */
  Flux<PushSubscription> findByUserId(Long userId);

  /**
   * Apunta que el servicio de push aceptó un envío.
   *
   * @param id identificador de la suscripción
   * @param at instante del envío
   * @return número de filas actualizadas
   */
  @Modifying
  @Query("UPDATE push_subscriptions SET last_success_at = :at WHERE id_push_subscription = :id")
  Mono<Long> markSucceeded(Long id, LocalDateTime at);
}
