package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.TagsApi;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TagService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para consultar los tags en uso.
 * Implementa el contrato {@link TagsApi} generado a partir de la especificación OpenAPI.
 * Solo expone lectura: los tags se crean y se borran desde la propia transacción.
 */
@RestController
@RequiredArgsConstructor
public class TagController implements TagsApi {

  private final TagService tagService;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<List<String>>> listTags(ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMapMany(tagService::findUsedTagNames)
        .collectList()
        .map(ResponseEntity::ok);
  }
}
