package com.sreyes.finscope.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa una respuesta de error estructurada para las excepciones manejadas en la aplicación.
 * Incluye un mensaje descriptivo, la fecha y hora del incidente, y el código de estado HTTP.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CategoryDto {
  private Long id;
  private String name;
}
