package ${basePackage}.mapper;

import ${basePackage}.dto.${dtoClass};
import ${basePackage}.entity.${entityName};
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ${className} {

    @Mapping(target = "id", ignore = true)
    ${entityName} toEntity(${dtoClass} dto);

    ${dtoClass} toDto(${entityName} entity);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(${dtoClass} dto, @MappingTarget ${entityName} entity);
}
