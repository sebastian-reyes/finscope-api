package com.sreyes.finscope.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa la suscripción a Web Push de un dispositivo.
 * Está mapeada a la tabla `push_subscriptions` en la base de datos.
 *
 * Es de un navegador concreto, no de un usuario: el móvil y el ordenador de la misma persona
 * son dos filas, y cada una se da de baja por su cuenta. La dirección identifica a ese
 * navegador ante su servicio de push y las dos claves son las que permiten cifrarle el
 * mensaje; ninguna de las tres sirve para leer nada de la cuenta.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("push_subscriptions")
public class PushSubscription {

  @Id
  @Column("id_push_subscription")
  private Long id;

  @Column("user_id")
  private Long userId;

  /**
   * Dirección del servicio de push a la que se manda el mensaje.
   */
  @Column("endpoint")
  private String endpoint;

  /**
   * Clave pública P-256 del navegador, en Base64 URL.
   */
  @Column("p256dh")
  private String p256dh;

  /**
   * Secreto de autenticación del navegador, en Base64 URL.
   */
  @Column("auth")
  private String auth;

  @Column("created_at")
  private LocalDateTime createdAt;

  /**
   * Último envío que el servicio aceptó. Nulo si todavía no se le ha mandado nada.
   */
  @Column("last_success_at")
  private LocalDateTime lastSuccessAt;
}
