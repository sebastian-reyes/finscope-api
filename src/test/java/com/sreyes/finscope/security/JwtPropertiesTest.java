package com.sreyes.finscope.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del margen de renovación concurrente. Es la ventana durante la que un token rotado
 * todavía sirve sin disparar la detección de robo, así que su valor por defecto y su tope son
 * parte de la seguridad y no un detalle de configuración.
 */
class JwtPropertiesTest {

  private static final String SECRET = "clave-de-firma-de-al-menos-32-caracteres";

  @Test
  @DisplayName("Sin margen configurado se usan treinta segundos")
  void defaultsTheReuseGrace() {
    assertThat(properties(null).refreshReuseGrace()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("Admite cero, que desactiva la tolerancia a renovaciones concurrentes")
  void acceptsZeroGrace() {
    assertThat(properties(Duration.ZERO).refreshReuseGrace()).isZero();
  }

  @Test
  @DisplayName("Rechaza un margen negativo o mayor que el tope")
  void rejectsGraceOutOfRange() {
    assertThatThrownBy(() -> properties(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> properties(Duration.ofMinutes(10)))
        .isInstanceOf(IllegalStateException.class);
  }

  /**
   * Construye una configuración válida con el margen indicado.
   *
   * @param grace margen de renovación concurrente
   * @return la configuración
   */
  private JwtProperties properties(Duration grace) {
    return new JwtProperties(SECRET, "finscope-api", "finscope-web", Duration.ofMinutes(15),
        Duration.ofDays(30), grace);
  }
}
