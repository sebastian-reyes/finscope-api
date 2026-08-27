package com.sreyes.finscope.util.mapper;

import com.sreyes.finscope.api.model.TagResponse;
import com.sreyes.finscope.model.query.TagUsage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper entre el tag con su uso, {@link TagUsage}, y los modelos de tag del contrato
 * OpenAPI.
 * El origen no es la entidad sino la proyección de consulta, porque el número de
 * transacciones lo calcula la base de datos al listar y no vive en la tabla de tags.
 */
@Mapper(componentModel = "spring")
public interface TagMapper {

  /**
   * Convierte un tag del catálogo en su representación de respuesta.
   *
   * @param tagUsage tag junto al número de transacciones que lo llevan
   * @return la representación del tag
   */
  @Mapping(target = "id", source = "tagId")
  @Mapping(target = "name", source = "tagName")
  TagResponse toResponse(TagUsage tagUsage);
}
