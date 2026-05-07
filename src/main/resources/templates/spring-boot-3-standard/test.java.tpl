package ${basePackage}.service;

import ${basePackage}.dto.${dtoClass};
import ${basePackage}.entity.${entityClass};
import ${basePackage}.error.ResourceNotFoundException;
import ${basePackage}.mapper.${mapperClass};
import ${basePackage}.repository.${repositoryClass};
${relatedRepositoryImports}
${idTypeImport}
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ${className} {

    @Mock
    private ${repositoryClass} repository;

    @Mock
    private ${mapperClass} mapper;
${relatedRepositoryMocks}

    @InjectMocks
    private ${serviceClass} service;

    private ${entityClass} entity;
    private ${dtoClass} dto;
    private ${idType} existingId;
    private ${idType} missingId;

    @BeforeEach
    void setUp() {
        entity = mock(${entityClass}.class);
        dto    = mock(${dtoClass}.class);
        existingId = ${testExistingId};
        missingId = ${testMissingId};
${relatedDtoDefaults}
    }

    @Test
    void create_savesAndReturnsDto() {
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(dto);

        ${dtoClass} result = service.create(dto);

        assertThat(result).isNotNull();
        verify(repository).save(entity);
    }

    @Test
    void findById_returnsDto_whenFound() {
        when(repository.findById(existingId)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(dto);

        ${dtoClass} result = service.findById(existingId);

        assertThat(result).isNotNull();
    }

    @Test
    void findById_throwsResourceNotFoundException_whenNotFound() {
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(missingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("${entityName}");
    }

    @Test
    void delete_deletesEntity_whenFound() {
        when(repository.findById(existingId)).thenReturn(Optional.of(entity));

        service.delete(existingId);

        verify(repository).delete(entity);
    }

    @Test
    void delete_throwsResourceNotFoundException_whenNotFound() {
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
