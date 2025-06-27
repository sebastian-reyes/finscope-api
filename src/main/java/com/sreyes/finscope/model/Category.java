package com.sreyes.finscope.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Table("categories")
public class Category {

  @Id
  @Column("id_category")
  private Long id;

  @Column("name_category")
  private String name;
}
