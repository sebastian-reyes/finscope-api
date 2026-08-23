package com.sreyes.finscope.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración del reloj de la aplicación.
 * Todo el código que necesita la hora actual pide este {@link Clock} en lugar de llamar a
 * {@code LocalDateTime.now()} sin argumentos: así la zona horaria queda explícita en un
 * único sitio y las pruebas pueden fijar el instante que necesiten.
 */
@Configuration
public class TimeConfig {

  /**
   * Reloj del sistema en la zona horaria del servidor.
   * Es el mismo comportamiento que tenía {@code LocalDateTime.now()}, ahora declarado a
   * propósito. Si algún día el servidor deja de vivir en la zona de los usuarios, este es
   * el punto donde fijarla.
   *
   * @return el reloj usado por toda la aplicación
   */
  @Bean
  public Clock clock() {
    return Clock.systemDefaultZone();
  }
}
