package com.sreyes.finscope.security;

import com.sreyes.finscope.util.constants.Constants;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Responde a las peticiones de un usuario autenticado que carece de permisos, manteniendo
 * el mismo formato de error que el resto de la API.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements ServerAccessDeniedHandler {

  private final ErrorResponseWriter errorResponseWriter;

  @Override
  @NullMarked
  public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
    return errorResponseWriter.write(exchange, HttpStatus.FORBIDDEN,
        "ACCESS_DENIED", Constants.ACCESS_DENIED);
  }
}
