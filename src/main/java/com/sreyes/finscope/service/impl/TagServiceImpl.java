package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.repository.TagRepository;
import com.sreyes.finscope.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Implementación del servicio {@link TagService}.
 * Se apoya en {@link TagRepository} para obtener los nombres de tag que el usuario ya ha
 * usado en sus transacciones.
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

  private final TagRepository tagRepository;

  @Override
  public Flux<String> findUsedTagNames(Long userId) {
    return tagRepository.findDistinctNamesByUserId(userId);
  }
}
