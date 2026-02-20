package ${basePackage}.repository;

import ${basePackage}.entity.${entityName};
import java.util.ArrayList;
import java.util.List;

public class ${className} {

    private final List<${entityName}> store = new ArrayList<>();

    public List<${entityName}> findAll() {
        return new ArrayList<>(store);
    }

    public void save(${entityName} entity) {
        store.add(entity);
    }
}
