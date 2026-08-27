package com.sreyes.finscope.controller;

import com.sreyes.finscope.api.TagsApi;
import com.sreyes.finscope.api.model.SaveTagRequest;
import com.sreyes.finscope.api.model.TagResponse;
import com.sreyes.finscope.security.AuthenticatedUser;
import com.sreyes.finscope.service.TagService;
import com.sreyes.finscope.util.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST para gestionar el catálogo de tags del usuario.
 * Implementa el contrato {@link TagsApi} generado a partir de la especificación OpenAPI.
 * El tag es una entidad compartida por las transacciones del usuario, de modo que
 * renombrarlo o eliminarlo alcanza a todas las que lo llevan; el alcance de cada operación
 * lo resuelve {@link TagService} y aquí solo se propaga el usuario autenticado.
 */
@RestController
@RequiredArgsConstructor
public class TagController implements TagsApi {

  private final TagService tagService;
  private final TagMapper tagMapper;
  private final AuthenticatedUser authenticatedUser;

  @Override
  public Mono<ResponseEntity<Flux<TagResponse>>> listTags(ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMapMany(tagService::findTags)
        .map(tagMapper::toResponse)
        .collectList()
        .map(tags -> ResponseEntity.ok(Flux.fromIterable(tags)));
  }

  @Override
  public Mono<ResponseEntity<TagResponse>> createTag(Mono<SaveTagRequest> saveTagRequest,
                                                     ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(saveTagRequest)
        .flatMap(tuple -> tagService.createTag(tuple.getT1(), tuple.getT2().getName()))
        .map(tagMapper::toResponse)
        .map(tag -> ResponseEntity.status(HttpStatus.CREATED).body(tag));
  }

  @Override
  public Mono<ResponseEntity<TagResponse>> updateTag(Long id,
                                                     Mono<SaveTagRequest> saveTagRequest,
                                                     ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .zipWith(saveTagRequest)
        .flatMap(tuple -> tagService.renameTag(tuple.getT1(), id, tuple.getT2().getName()))
        .map(tagMapper::toResponse)
        .map(ResponseEntity::ok);
  }

  @Override
  public Mono<ResponseEntity<Void>> deleteTag(Long id, ServerWebExchange exchange) {
    return authenticatedUser.currentUserId()
        .flatMap(userId -> tagService.deleteTag(userId, id))
        .thenReturn(ResponseEntity.noContent().build());
  }
}
