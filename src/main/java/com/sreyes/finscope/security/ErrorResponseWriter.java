package com.sreyes.finscope.security;

import com.sreyes.finscope.api.model.ErrorResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe una respuesta de error directamente sobre la respuesta HTTP.
 * Los fallos de seguridad se producen en la cadena de filtros, antes de llegar a un
 * controlador, por lo que el manejador global de excepciones no los ve y hay que
 * serializar la respuesta aquí para conservar un formato de error único en toda la API.
 */
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {

  private final ObjectMapper objectMapper;
  private final Clock clock;

  /**
   * Escribe la respuesta de error indicada y completa el intercambio.
   *
   * @param exchange intercambio HTTP en curso
   * @param status   estado HTTP de la respuesta
   * @param code     código estable del error
   * @param message  mensaje descriptivo del error
   * @return Mono vacío al completar la escritura
   */
  public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String code,
                          String message) {
    exchange.getResponse().setStatusCode(status);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    ErrorResponse body = new ErrorResponse(LocalDateTime.now(clock), status.value(), code, message);
    DataBuffer buffer = exchange.getResponse().bufferFactory()
        .wrap(objectMapper.writeValueAsBytes(body));
    return exchange.getResponse().writeWith(Mono.just(buffer));
  }
}
