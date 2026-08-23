package com.sreyes.finscope.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa un token de refresco emitido a un usuario.
 * Está mapeada a la tabla `refresh_tokens` en la base de datos.
 * Solo se almacena el hash del token, de modo que el valor original nunca queda
 * persistido y el registro sirve para poder revocarlo.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("refresh_tokens")
public class RefreshToken {

  @Id
  @Column("id_refresh_token")
  private Long id;

  @Column("user_id")
  private Long userId;

  @Column("token_hash")
  private String tokenHash;

  @Column("expires_at")
  private LocalDateTime expiresAt;

  @Column("revoked")
  private boolean revoked;

  @Column("created_at")
  private LocalDateTime createdAt;
}
