package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.model.dto.CategoryDTO;
import com.sreyes.finscope.model.dto.TransactionResponseDTO;
import com.sreyes.finscope.model.dto.TransactionTypeDTO;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionType;
import org.springframework.stereotype.Component;

@Component
public class MapperUtil {

  public CategoryDTO buildCategoryDTO(Category category) {
    return CategoryDTO.builder()
        .id(category.getId())
        .name(category.getName())
        .build();
  }

  public TransactionTypeDTO buildTransactionTypeDTO(TransactionType transactionType) {
    return TransactionTypeDTO.builder()
        .id(transactionType.getId())
        .name(transactionType.getName())
        .build();
  }

  public TransactionResponseDTO buildTransactionResponseDTO(Transaction transaction,
                                                            Category category,
                                                            TransactionType transactionType) {
    return TransactionResponseDTO.builder()
        .id(transaction.getId())
        .amount(transaction.getAmount())
        .description(transaction.getDescription())
        .date(transaction.getDate())
        .category(buildCategoryDTO(category))
        .transactionType(buildTransactionTypeDTO(transactionType))
        .build();
  }
}
