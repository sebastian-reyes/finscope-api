package com.sreyes.finscope.service.impl;

import com.sreyes.finscope.exception.custom.CategoryNotFoundException;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.repository.CategoryRepository;
import com.sreyes.finscope.service.CategoryService;
import com.sreyes.finscope.util.constants.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementación del servicio {@link CategoryService} para la gestión de categorías.
 * Proporciona operaciones reactivas para consultar, guardar y eliminar categorías.
 * Utiliza {@link CategoryRepository} para el acceso a datos.
 * Lanza {@link CategoryNotFoundException} cuando no se encuentra una categoría.
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

  private final CategoryRepository categoryRepository;

  @Override
  public Flux<Category> findAllCategories() {
    return categoryRepository.findAll();
  }

  @Override
  public Mono<Category> findCategoryById(Long id) {
    return categoryRepository.findById(id)
        .switchIfEmpty(Mono
            .error(new CategoryNotFoundException(Constants.CATEGORY_NOT_FOUND + id)));
  }

  @Override
  public Mono<Category> saveCategory(Category category) {
    return categoryRepository.save(category);
  }

  @Override
  public Mono<Void> deleteCategoryById(Long id) {
    return categoryRepository.existsById(id)
        .flatMap(exists -> {
          if (Boolean.TRUE.equals(exists)) {
            return categoryRepository.deleteById(id);
          } else {
            return Mono
                .error(new CategoryNotFoundException(Constants.CATEGORY_NOT_FOUND + id));
          }
        });
  }
}
