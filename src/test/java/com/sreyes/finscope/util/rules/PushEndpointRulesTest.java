package com.sreyes.finscope.util.rules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de {@link PushEndpointRules}. La API hace peticiones a la dirección que manda el
 * cliente, así que lo que se comprueba aquí es que no pueda usarse para llamar a otro sitio.
 */
class PushEndpointRulesTest {

  @Test
  @DisplayName("Acepta los servicios de push de Chrome, Firefox, Safari y Edge")
  void acceptsKnownPushServices() {
    assertThat(PushEndpointRules.isAllowed("https://fcm.googleapis.com/fcm/send/abc:APA91b"))
        .isTrue();
    assertThat(PushEndpointRules.isAllowed(
        "https://updates.push.services.mozilla.com/wpush/v2/gAAAA")).isTrue();
    assertThat(PushEndpointRules.isAllowed("https://web.push.apple.com/QGuQyavXutnMb")).isTrue();
    assertThat(PushEndpointRules.isAllowed(
        "https://wns2-par02p.notify.windows.com/w/?token=BQYAAA")).isTrue();
  }

  @Test
  @DisplayName("Rechaza direcciones internas, sin cifrar o con otro puerto")
  void rejectsEverythingElse() {
    assertThat(PushEndpointRules.isAllowed("https://finscope-api-ok2a:10000/push/test")).isFalse();
    assertThat(PushEndpointRules.isAllowed("http://fcm.googleapis.com/fcm/send/abc")).isFalse();
    assertThat(PushEndpointRules.isAllowed("https://fcm.googleapis.com:8443/fcm/send/abc"))
        .isFalse();
    assertThat(PushEndpointRules.isAllowed("https://169.254.169.254/latest/meta-data")).isFalse();
    assertThat(PushEndpointRules.isAllowed("https://evil.com@fcm.googleapis.com/x")).isFalse();
    assertThat(PushEndpointRules.isAllowed("https://fcm.googleapis.com.evil.com/x")).isFalse();
    assertThat(PushEndpointRules.isAllowed("https://notpush.apple.com/x")).isFalse();
    assertThat(PushEndpointRules.isAllowed("no es una url")).isFalse();
    assertThat(PushEndpointRules.isAllowed(null)).isFalse();
  }
}
