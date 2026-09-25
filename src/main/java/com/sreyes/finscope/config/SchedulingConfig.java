package com.sreyes.finscope.config;

import com.sreyes.finscope.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

/**
 * Tareas que la API ejecuta por su cuenta.
 *
 * <p>Viven dentro del propio proceso y no en un cron externo porque la instancia de producción
 * es de pago y no se duerme: está viva las 24 horas y puede programarse sola. Si algún día se
 * pasara a un plan que suspende el servicio sin tráfico, esto dejaría de ejecutarse en silencio
 * y haría falta algo de fuera que lo despierte.</p>
 *
 * <p>La tarea corre cada hora y no una vez al día a propósito. Un reinicio o un despliegue justo
 * a la hora de avisar se comería los avisos de ese día; cada hora, lo que no salió sale en la
 * siguiente, y el registro de avisos enviados impide que lo que sí salió se repita.</p>
 *
 * <p>Se puede apagar con {@code finscope.push.scheduler-enabled=false}, que es lo que usan las
 * pruebas que arrancan el contexto entero.</p>
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "finscope.push", name = "scheduler-enabled",
    havingValue = "true", matchIfMissing = true)
public class  SchedulingConfig {

  private final NotificationService notificationService;

  /**
   * Pasada de los avisos programados, en el minuto 5 de cada hora.
   * No en el minuto cero, que es cuando arranca medio mundo sus tareas y cuando más tarda en
   * contestar un servicio compartido.
   *
   * @return la pasada, que Spring suscribe
   */
  @Scheduled(cron = "0 5 * * * *")
  public Mono<Void> scheduledNotifications() {
    return notificationService.runScheduled();
  }
}
