package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.AuthApi;
import com.sreyes.finscope.api.model.AuthResponse;
import com.sreyes.finscope.api.model.LoginRequest;
import com.sreyes.finscope.api.model.RefreshTokenRequest;
import com.sreyes.finscope.api.model.RegisterRequest;
import com.sreyes.finscope.api.model.UpdateUserRequest;
import com.sreyes.finscope.api.model.UserResponse;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para el alta de usuarios y la gestión de sus credenciales.
 * Implementa el contrato {@link AuthApi} generado a partir de la especificación OpenAPI.
 * Todas sus rutas son públicas salvo la consulta del usuario en curso, que necesita un
 * token válido y por eso resuelve el usuario desde el contexto de seguridad.
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  private final AuthService authService;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<AuthResponse>> register(Mono<RegisterRequest> registerRequest,
                                                     ServerWebExchange exchange) {
    return registerRequest
        .flatMap(authService::register)
        .map(auth -> ResponseEntity.status(HttpStatus.CREATED).body(auth));
  }

  @Override
  public Mono<ResponseEntity<AuthResponse>> login(Mono<LoginRequest> loginRequest,
                                                  ServerWebExchange exchange) {
    return loginRequest
        .flatMap(authService::login)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<AuthResponse>> refreshToken(
      Mono<RefreshTokenRequest> refreshTokenRequest, ServerWebExchange exchange) {
    return refreshTokenRequest
        .flatMap(request -> authService.refresh(request.getRefreshToken()))
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> logout(Mono<RefreshTokenRequest> refreshTokenRequest,
                                           ServerWebExchange exchange) {
    return refreshTokenRequest
        .flatMap(request -> authService.logout(request.getRefreshToken()))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<UserResponse>> getCurrentUser(ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(authService::getUser)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<UserResponse>> updateCurrentUser(
      Mono<UpdateUserRequest> updateUserRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> updateUserRequest
            .flatMap(request -> authService.updateUser(userId, request)))
        .map(ResponseEntity::ok);
  }
}
