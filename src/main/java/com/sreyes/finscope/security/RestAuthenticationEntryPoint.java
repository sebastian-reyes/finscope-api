package com.sreyes.finscope.security;

import com.sreyes.finscope.util.constants.Constants;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Responde a las peticiones que llegan sin credenciales válidas.
 * Sin esta pieza Spring Security devolvería una respuesta vacía con la cabecera
 * WWW-Authenticate, incoherente con el formato de error del resto de la API.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

  private final ErrorResponseWriter errorResponseWriter;

  @Override
  @NullMarked
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
    return errorResponseWriter.write(exchange, HttpStatus.UNAUTHORIZED,
        "UNAUTHENTICATED", Constants.AUTHENTICATION_REQUIRED);
  }
}
