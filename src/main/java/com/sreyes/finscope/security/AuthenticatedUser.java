package com.sreyes.finscope.security;

import com.sreyes.finscope.exception.custom.UnauthenticatedException;
import com.sreyes.finscope.util.constants.Constants;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * Da acceso al usuario autenticado desde los controladores.
 * Las interfaces generadas a partir del contrato OpenAPI tienen una firma fija, por lo que
 * el identificador del usuario no puede recibirse como parámetro y se obtiene del contexto
 * reactivo de seguridad. A partir de ahí se propaga de forma explícita hacia los servicios.
 */
@Component
public class AuthenticatedUser {

  /**
   * Obtiene el identificador del usuario autenticado en la petición en curso.
   *
   * @return identificador del usuario envuelto en Mono
   */
  public Mono<Long> currentUserId() {
    return ReactiveSecurityContextHolder.getContext()
        .map(context -> Objects.requireNonNull(context.getAuthentication()).getPrincipal())
        .filter(Jwt.class::isInstance)
        .cast(Jwt.class)
        .map(jwt -> Long.valueOf(jwt.getSubject()))
        .switchIfEmpty(Mono.error(
            new UnauthenticatedException(Constants.AUTHENTICATION_REQUIRED)));
  }
}
