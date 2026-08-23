package com.sreyes.finscope.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa un usuario de la aplicación.
 * Está mapeada a la tabla `users` en la base de datos.
 * Todos los datos financieros pertenecen a un usuario y están aislados entre cuentas.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("users")
public class User {

  @Id
  @Column("id_user")
  private Long id;

  @Column("email")
  private String email;

  /**
   * Hash BCrypt de la contraseña. Es nulo mientras la cuenta no tenga credenciales
   * locales, por ejemplo en la cuenta sembrada al adoptar datos previos al modelo
   * multiusuario o en una cuenta creada por un proveedor externo.
   */
  @Column("password_hash")
  private String passwordHash;

  @Column("display_name")
  private String displayName;

  @Column("active")
  private boolean active;

  @Column("created_at")
  private LocalDateTime createdAt;
}
