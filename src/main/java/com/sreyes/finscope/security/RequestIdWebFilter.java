package com.sreyes.finscope.security;

import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Asigna un identificador a cada petición y lo devuelve en la respuesta.
 * Sirve para poder seguir una petición concreta entre los registros sin tener que apoyarse
 * en la hora o en el usuario, que ni identifican ni distinguen peticiones simultáneas.
 *
 * <p>Se acepta el identificador que traiga el cliente para poder correlacionar con lo que
 * ya haya registrado por su parte, pero solo si tiene la forma esperada: llega en una
 * cabecera y termina en los registros y en la respuesta, así que un valor libre permitiría
 * colar saltos de línea o cabeceras adicionales.</p>
 */
@Component
@Order(RequestIdWebFilter.ORDER)
public class RequestIdWebFilter implements WebFilter {

  /**
   * Posición del filtro en la cadena. Es el primero, para que todo lo demás —incluido el
   * rechazo por exceso de peticiones— quede ya asociado a un identificador.
   */
  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

  /**
   * Nombre de la cabecera que transporta el identificador.
   */
  public static final String REQUEST_ID_HEADER = "X-Request-ID";

  /**
   * Clave con la que el identificador viaja en el contexto reactivo y en los atributos del
   * intercambio, desde donde puede recuperarse para registrarlo junto a un evento.
   */
  public static final String REQUEST_ID_KEY = "requestId";

  /**
   * Forma admitida para un identificador recibido del cliente.
   */
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

  @Override
  @NullMarked
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = resolveRequestId(exchange);
    exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
    exchange.getAttributes().put(REQUEST_ID_KEY, requestId);
    return chain.filter(exchange)
        .contextWrite(context -> context.put(REQUEST_ID_KEY, requestId));
  }

  /**
   * Obtiene el identificador de la petición, conservando el del cliente solo si es seguro.
   *
   * @param exchange intercambio HTTP en curso
   * @return el identificador de la petición
   */
  private String resolveRequestId(ServerWebExchange exchange) {
    String provided = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
    return provided != null && SAFE_REQUEST_ID.matcher(provided).matches()
        ? provided
        : UUID.randomUUID().toString();
  }
}
