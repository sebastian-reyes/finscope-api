package com.sreyes.finscope.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa un enlace de un solo uso mandado al correo de un usuario.
 * Está mapeada a la tabla `account_tokens` en la base de datos.
 *
 * <p>Los tres propósitos comparten tabla porque comparten mecanismo: un valor aleatorio que
 * se manda por correo, caduca pronto y deja de valer en cuanto se usa. Lo único que cambia
 * entre ellos es qué se hace al consumirlo.</p>
 *
 * <p>Del valor entregado al usuario solo se guarda su hash, igual que en
 * {@link RefreshToken}: quien pueda leer la tabla no puede fabricar con ella un enlace
 * utilizable.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("account_tokens")
public class AccountToken {

  /**
   * Demuestra que la cuenta recibe correo en la dirección que tiene puesta.
   */
  public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";

  /**
   * Permite establecer una contraseña nueva sin conocer la anterior.
   */
  public static final String PASSWORD_RESET = "PASSWORD_RESET";

  /**
   * Aplica el cambio de correo pendiente guardado en {@link #targetEmail}.
   */
  public static final String EMAIL_CHANGE = "EMAIL_CHANGE";

  @Id
  @Column("id_account_token")
  private Long id;

  @Column("user_id")
  private Long userId;

  /**
   * Qué se hace al consumirlo. Uno de los tres valores declarados en esta clase, que son
   * los mismos que admite la restricción de la tabla.
   */
  @Column("purpose")
  private String purpose;

  @Column("token_hash")
  private String tokenHash;

  /**
   * Dirección a la que se moverá la cuenta. Solo la lleva {@link #EMAIL_CHANGE}, y en ese
   * caso nunca es nula.
   */
  @Column("target_email")
  private String targetEmail;

  @Column("expires_at")
  private LocalDateTime expiresAt;

  /**
   * Cuándo se usó. Mientras sea nulo el enlace sigue sirviendo, si no ha caducado.
   */
  @Column("consumed_at")
  private LocalDateTime consumedAt;

  @Column("created_at")
  private LocalDateTime createdAt;
}
