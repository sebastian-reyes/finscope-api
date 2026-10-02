package com.sreyes.finscope.config;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
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

  private static final String DEFAULT_ZONE = "America/Lima";

  /**
   * Reloj del sistema en la zona horaria de los usuarios, no en la del servidor.
   * Las fechas se guardan como {@code LocalDateTime}, sin zona, así que la hora que marque
   * este reloj es la que queda escrita. El contenedor corre en UTC, y con su zona un fijo
   * pagado a las 10:00 de Lima se guardaba a las 15:00, y desde las 19:00 «hoy» ya era
   * mañana al decidir qué fijos están vencidos.
   *
   * @param zone zona horaria de los usuarios; vacía usa {@value #DEFAULT_ZONE}
   * @return el reloj usado por toda la aplicación
   * @throws IllegalStateException si la zona configurada no existe
   */
  @Bean
  public Clock clock(@Value("${finscope.zone:" + DEFAULT_ZONE + "}") String zone) {
    String configured = zone == null || zone.isBlank() ? DEFAULT_ZONE : zone.strip();
    try {
      return Clock.system(ZoneId.of(configured));
    } catch (DateTimeException ex) {
      throw new IllegalStateException("finscope.zone is not a valid time zone: " + configured, ex);
    }
  }
}
