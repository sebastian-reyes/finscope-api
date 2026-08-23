package com.sreyes.finscope.service;

import com.sreyes.finscope.api.model.AuthResponse;
import com.sreyes.finscope.api.model.LoginRequest;
import com.sreyes.finscope.api.model.RegisterRequest;
import com.sreyes.finscope.api.model.UserResponse;
import reactor.core.publisher.Mono;

/**
 * Servicio de registro y autenticación de usuarios.
 * Define las operaciones reactivas que emiten y revocan las credenciales con las que el
 * cliente accede al resto de la API.
 */
public interface AuthService {

  /**
   * Registra un usuario nuevo y emite sus credenciales.
   *
   * @param request datos de alta del usuario
   * @return credenciales del usuario recién registrado envueltas en Mono
   */
  Mono<AuthResponse> register(RegisterRequest request);

  /**
   * Autentica a un usuario y emite un par de tokens.
   *
   * @param request credenciales de acceso
   * @return credenciales emitidas envueltas en Mono
   */
  Mono<AuthResponse> login(LoginRequest request);

  /**
   * Consume un token de refresco y emite un par nuevo.
   *
   * @param refreshToken token de refresco recibido del cliente
   * @return credenciales renovadas envueltas en Mono
   */
  Mono<AuthResponse> refresh(String refreshToken);

  /**
   * Revoca el token de refresco indicado para cerrar la sesión.
   *
   * @param refreshToken token de refresco a revocar
   * @return Mono vacío al completar la revocación
   */
  Mono<Void> logout(String refreshToken);

  /**
   * Obtiene los datos del usuario indicado.
   *
   * @param userId identificador del usuario
   * @return datos del usuario envueltos en Mono
   */
  Mono<UserResponse> getUser(Long userId);
}
