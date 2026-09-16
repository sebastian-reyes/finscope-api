package com.sreyes.finscope.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la validación de la configuración de correo. Lo que protegen es el arranque sin
 * variables: Spring enlaza el marcador sin resolver como texto, y sin estas comprobaciones la
 * aplicación arrancaba mandando enlaces rotos.
 */
class MailPropertiesTest {

  private static final String FROM = "FinScope <no-reply@fin-scope.app>";
  private static final String BASE_URL = "https://fin-scope.app";

  @Test
  @DisplayName("Acepta un remitente con nombre y un origen web, y quita la barra final")
  void acceptsValidConfiguration() {
    MailProperties properties = new MailProperties(FROM, BASE_URL + "/", null, null, null);

    assertThat(properties.link("/reset-password", "abc"))
        .isEqualTo("https://fin-scope.app/reset-password?token=abc");
  }

  @Test
  @DisplayName("Rechaza el origen web cuando la variable APP_BASE_URL no está definida")
  void rejectsUnresolvedBaseUrl() {
    assertThatThrownBy(() -> new MailProperties(FROM, "${APP_BASE_URL}", null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("APP_BASE_URL");
  }

  @Test
  @DisplayName("Rechaza el remitente cuando la variable MAIL_FROM no está definida")
  void rejectsUnresolvedFrom() {
    assertThatThrownBy(() -> new MailProperties("${MAIL_FROM}", BASE_URL, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MAIL_FROM");
  }

  @Test
  @DisplayName("Rechaza un remitente que no es una dirección de correo")
  void rejectsInvalidFrom() {
    assertThatThrownBy(() -> new MailProperties("FinScope", BASE_URL, null, null, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("finscope.mail.from");
  }

  @Test
  @DisplayName("Rechaza un origen que no es una dirección web absoluta")
  void rejectsNonWebBaseUrl() {
    for (String invalid : new String[] {"fin-scope.app", "ftp://fin-scope.app",
        "https://fin-scope.app?x=1"}) {
      assertThatThrownBy(() -> new MailProperties(FROM, invalid, null, null, null))
          .as(invalid)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("finscope.mail.app-base-url");
    }
  }
}
