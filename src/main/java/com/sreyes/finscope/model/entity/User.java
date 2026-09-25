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

  /**
   * Si se ha demostrado que la cuenta recibe correo en la dirección que tiene puesta.
   * No condiciona el acceso: una cuenta sin verificar funciona igual. Lo que se pierde sin
   * verificar es poder recuperar la contraseña, porque el enlace iría a una dirección de la
   * que nadie ha comprobado que exista.
   */
  @Column("email_verified")
  private boolean emailVerified;

  @Column("display_name")
  private String displayName;

  /**
   * Imagen de perfil elegida, por el nombre de la ilustración que pinta el cliente. Nula
   * mientras no se elija, y entonces el perfil se pinta con las iniciales.
   */
  @Column("avatar")
  private String avatar;

  @Column("active")
  private boolean active;

  @Column("created_at")
  private LocalDateTime createdAt;
}
