package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.model.dto.TransactionResponseDto;
import com.sreyes.finscope.model.entity.Category;
import com.sreyes.finscope.model.entity.Transaction;
import com.sreyes.finscope.model.entity.TransactionType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

  @Mapping(target = "id", source = "transaction.id")
  @Mapping(target = "amount", source = "transaction.amount")
  @Mapping(target = "description", source = "transaction.description")
  @Mapping(target = "date", source = "transaction.date")
  @Mapping(target = "category", source = "category")
  @Mapping(target = "transactionType", source = "transactionType")
  TransactionResponseDto toDto(Transaction transaction, Category category, TransactionType transactionType);
}
