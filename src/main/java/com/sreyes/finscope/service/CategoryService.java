package com.sreyes.finscope.service;

import com.sreyes.finscope.model.entity.Category;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoryService {

  Flux<Category> findAllCategories();
  Mono<Category> findCategoryById(Long id);
  Mono<Category> saveCategory(Category category);
  Mono<Category> updateCategory(Long id, Category category);
  Mono<Void> deleteCategoryById(Long id);
}
