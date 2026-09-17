package com.sreyes.finscope.service;

import reactor.core.publisher.Mono;

/**
 * Decide qué avisos programados tocan y los manda.
 *
 * Es lo único de la API que trabaja sin que nadie lo pida: lo dispara una tarea cada hora.
 * Cada pasada mira qué fijos vencen hoy o mañana y qué presupuestos del mes llegaron a su
 * umbral, y avisa de lo que todavía no se había avisado. Repetir una pasada no repite avisos.
 */
public interface NotificationService {

  /**
   * Ejecuta una pasada de los avisos programados.
   * Fuera de la ventana horaria configurada no hace nada: lo pendiente sale en la primera
   * pasada dentro de ella.
   *
   * @return Mono vacío al terminar; los fallos de un aviso no interrumpen los demás
   */
  Mono<Void> runScheduled();
}
