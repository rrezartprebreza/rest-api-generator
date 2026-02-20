package ${basePackage}.service;

import ${basePackage}.entity.${entityName};
import ${basePackage}.repository.${repositoryClass};
import java.util.List;

public class ${className} {

    private final ${repositoryClass} repository;

    public ${className}(${repositoryClass} repository) {
        this.repository = repository;
    }

    public List<${entityName}> findAll() {
        return repository.findAll();
    }

    public void create(${entityName} entity) {
        repository.save(entity);
    }
}
