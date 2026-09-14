package com.sreyes.finscope.service;

import com.sreyes.finscope.api.model.ChangeEmailRequest;
import com.sreyes.finscope.api.model.ResetPasswordRequest;
import reactor.core.publisher.Mono;

/**
 * Operaciones sobre el correo y la contraseña de una cuenta que pasan por el buzón.
 * Viven aparte de {@link AuthService} porque responden a otra pregunta: aquel emite y revoca
 * las credenciales con las que se accede, y este comprueba y cambia con qué se accede.
 *
 * <p>Las tres son la misma pieza vista tres veces —un enlace de un solo uso que caduca— y por
 * eso comparten servicio: la generación, la caducidad y el consumo se escriben una vez.</p>
 */
public interface AccountService {

  /**
   * Manda al correo de la cuenta el enlace con el que darlo por verificado.
   * Sobre una cuenta ya verificada no hace nada y completa igual: la respuesta no debe
   * permitir averiguar el estado de una cuenta ajena.
   *
   * @param userId identificador del usuario
   * @return Mono vacío al dejar el correo encaminado
   */
  Mono<Void> sendEmailVerification(Long userId);

  /**
   * Da por verificado el correo de la cuenta a la que pertenece el enlace.
   *
   * @param token valor del enlace recibido por correo
   * @return Mono vacío al completar la verificación
   */
  Mono<Void> confirmEmailVerification(String token);

  /**
   * Pide mover la cuenta a otra dirección, sin aplicarlo todavía.
   * Exige la contraseña en curso y deja el cambio pendiente hasta que se confirme desde la
   * dirección nueva.
   *
   * @param userId  identificador del usuario
   * @param request dirección nueva y contraseña en curso
   * @return Mono vacío al dejar el correo encaminado
   */
  Mono<Void> requestEmailChange(Long userId, ChangeEmailRequest request);

  /**
   * Aplica el cambio de correo pendiente al que pertenece el enlace.
   *
   * @param token valor del enlace recibido en la dirección nueva
   * @return Mono vacío al completar el cambio
   */
  Mono<Void> confirmEmailChange(String token);

  /**
   * Manda el enlace con el que establecer una contraseña nueva.
   * Completa igual exista o no una cuenta con ese correo: distinguir los dos casos
   * convertiría la operación en una lista de quién está registrado.
   *
   * @param email correo de la cuenta que quiere recuperarse
   * @return Mono vacío al dejar el correo encaminado
   */
  Mono<Void> requestPasswordReset(String email);

  /**
   * Establece la contraseña de la cuenta a la que pertenece el enlace y cierra sus sesiones.
   *
   * @param request enlace recibido por correo y contraseña nueva
   * @return Mono vacío al completar el cambio
   */
  Mono<Void> resetPassword(ResetPasswordRequest request);
}
