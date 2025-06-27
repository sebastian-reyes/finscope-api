package com.sreyes.finscope.repository;

import com.sreyes.finscope.model.Category;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends R2dbcRepository<Category, Long> {
}
