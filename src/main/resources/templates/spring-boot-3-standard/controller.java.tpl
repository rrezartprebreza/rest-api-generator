package ${basePackage}.controller;

import ${basePackage}.dto.${dtoClass};
import ${collaboratorImport};
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${resourcePath}")
public class ${className} {

    private final ${collaboratorClass} collaborator;

    public ${className}(${collaboratorClass} collaborator) {
        this.collaborator = collaborator;
    }

    @GetMapping
    public Page<${dtoClass}> list() {
        return collaborator.findAll(0, 20, "id", "asc", null);
    }

    @GetMapping("/search")
    public Page<${dtoClass}> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(required = false) String filter
    ) {
        return collaborator.findAll(page, size, sortBy, sortDir, filter);
    }

    @GetMapping("/{id}")
    public ${dtoClass} findById(@PathVariable Long id) {
        ${findByIdCall}
    }

    @PostMapping
    public ${dtoClass} create(@RequestBody ${dtoClass} entity) {
        ${createCall}
    }

    @PutMapping("/{id}")
    public ${dtoClass} update(@PathVariable Long id, @RequestBody ${dtoClass} entity) {
        ${updateCall}
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        ${deleteCall}
    }
}
