package io.restapigen.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class ApiSpecification {
    public final String projectName;
    public final String basePackage;
    public final List<EntityDefinition> entities;
    public final List<String> suggestions;

    @JsonCreator
    public ApiSpecification(
            @JsonProperty("projectName") String projectName,
            @JsonProperty("basePackage") String basePackage,
            @JsonProperty("entities") List<EntityDefinition> entities,
            @JsonProperty("suggestions") List<String> suggestions
    ) {
        this.projectName = Objects.requireNonNull(projectName, "projectName");
        this.basePackage = Objects.requireNonNull(basePackage, "basePackage");
        this.entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        this.suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions"));
    }
}
