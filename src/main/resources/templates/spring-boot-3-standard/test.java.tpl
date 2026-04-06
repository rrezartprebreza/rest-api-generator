package ${basePackage}.service;

import ${basePackage}.dto.${dtoClass};
import ${basePackage}.entity.${entityName};
import ${basePackage}.error.ResourceNotFoundException;
import ${basePackage}.mapper.${mapperClass};
import ${basePackage}.repository.${repositoryClass};
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

    @InjectMocks
    private ${serviceClass} service;

    private ${entityName} entity;
    private ${dtoClass} dto;

    @BeforeEach
    void setUp() {
        entity = new ${entityName}();
        dto    = new ${dtoClass}();
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
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(mapper.toDto(entity)).thenReturn(dto);

        ${dtoClass} result = service.findById(1L);

        assertThat(result).isNotNull();
    }

    @Test
    void findById_throwsResourceNotFoundException_whenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("${entityName}");
    }

    @Test
    void delete_deletesEntity_whenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        service.delete(1L);

        verify(repository).delete(entity);
    }

    @Test
    void delete_throwsResourceNotFoundException_whenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
