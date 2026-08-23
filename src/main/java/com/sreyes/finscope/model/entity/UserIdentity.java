package com.sreyes.finscope.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entidad que representa una identidad con la que un usuario puede autenticarse.
 * Está mapeada a la tabla `user_identities` en la base de datos.
 * Hoy solo se emplea el proveedor local, pero la tabla permite asociar proveedores
 * externos a la misma cuenta sin modificar la entidad {@link User}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("user_identities")
public class UserIdentity {

  /**
   * Proveedor de las credenciales gestionadas por la propia aplicación.
   */
  public static final String LOCAL_PROVIDER = "LOCAL";

  @Id
  @Column("id_identity")
  private Long id;

  @Column("user_id")
  private Long userId;

  @Column("provider")
  private String provider;

  /**
   * Identificador del usuario dentro del proveedor. Para el proveedor local es su correo.
   */
  @Column("subject")
  private String subject;
}
