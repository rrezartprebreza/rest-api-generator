package ${basePackage}.controller;

import ${basePackage}.entity.${entityName};
import ${collaboratorImport};
import java.util.List;

public class ${className} {

    private final ${collaboratorClass} collaborator;

    public ${className}(${collaboratorClass} collaborator) {
        this.collaborator = collaborator;
    }

    public List<${entityName}> list() {
        return collaborator.findAll();
    }

    public void create(${entityName} entity) {
        ${createCall}
    }
}
