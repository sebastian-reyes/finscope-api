package com.sreyes.finscope.exception.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa una respuesta de error estructurada para las excepciones manejadas en la aplicación.
 * Incluye un mensaje descriptivo, la fecha y hora del incidente, y el código de estado HTTP.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
  private String message;
  private LocalDateTime timestamp;
  private int status;
}
