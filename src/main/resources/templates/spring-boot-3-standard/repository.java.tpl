package ${basePackage}.repository;

import ${basePackage}.entity.${entityName};
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;
${relationImports}

@Repository
public interface ${className} extends JpaRepository<${entityName}, Long>, JpaSpecificationExecutor<${entityName}> {
${relationMethods}}
