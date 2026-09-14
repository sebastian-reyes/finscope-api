package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.AuthApi;
import com.sreyes.finscope.api.model.AccountTokenRequest;
import com.sreyes.finscope.api.model.AuthResponse;
import com.sreyes.finscope.api.model.ChangeEmailRequest;
import com.sreyes.finscope.api.model.ForgotPasswordRequest;
import com.sreyes.finscope.api.model.LoginRequest;
import com.sreyes.finscope.api.model.RefreshTokenRequest;
import com.sreyes.finscope.api.model.RegisterRequest;
import com.sreyes.finscope.api.model.ResetPasswordRequest;
import com.sreyes.finscope.api.model.UpdateUserRequest;
import com.sreyes.finscope.api.model.UserResponse;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.AccountService;
import com.sreyes.finscope.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para el alta de usuarios y la gestión de sus credenciales.
 * Implementa el contrato {@link AuthApi} generado a partir de la especificación OpenAPI.
 *
 * <p>Sus rutas se reparten en dos grupos. Las que trabajan sobre la cuenta en curso resuelven
 * el usuario desde el contexto de seguridad y exigen un token válido. Las que consumen un
 * enlace recibido por correo son públicas a propósito: el enlace puede abrirse en un
 * navegador distinto de aquel en el que se pidió, y es él mismo la credencial.</p>
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

  private final AuthService authService;
  private final AccountService accountService;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<AuthResponse>> register(@NonNull Mono<RegisterRequest> registerRequest,
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

  @Override
  public Mono<ResponseEntity<Void>> sendEmailVerification(ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(accountService::sendEmailVerification)
        .thenReturn(ResponseEntity.accepted().build());
  }

  @Override
  public Mono<ResponseEntity<Void>> confirmEmailVerification(
      Mono<AccountTokenRequest> accountTokenRequest, ServerWebExchange exchange) {
    return accountTokenRequest
        .flatMap(request -> accountService.confirmEmailVerification(request.getToken()))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<Void>> requestEmailChange(
      Mono<ChangeEmailRequest> changeEmailRequest, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> changeEmailRequest
            .flatMap(request -> accountService.requestEmailChange(userId, request)))
        .thenReturn(ResponseEntity.accepted().build());
  }

  @Override
  public Mono<ResponseEntity<Void>> confirmEmailChange(
      Mono<AccountTokenRequest> accountTokenRequest, ServerWebExchange exchange) {
    return accountTokenRequest
        .flatMap(request -> accountService.confirmEmailChange(request.getToken()))
        .thenReturn(ResponseEntity.noContent().build());
  }

  @Override
  public Mono<ResponseEntity<Void>> requestPasswordReset(
      Mono<ForgotPasswordRequest> forgotPasswordRequest, ServerWebExchange exchange) {
    return forgotPasswordRequest
        .flatMap(request -> accountService.requestPasswordReset(request.getEmail()))
        .thenReturn(ResponseEntity.accepted().build());
  }

  @Override
  public Mono<ResponseEntity<Void>> resetPassword(
      Mono<ResetPasswordRequest> resetPasswordRequest, ServerWebExchange exchange) {
    return resetPasswordRequest
        .flatMap(accountService::resetPassword)
        .thenReturn(ResponseEntity.noContent().build());
  }
}
