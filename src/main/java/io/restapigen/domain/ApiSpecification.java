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
    /** Security hint parsed from prompt phrases like "JWT authentication". Values: "none", "jwt", "oauth2", "basic". */
    public final String securityHint;

    @JsonCreator
    public ApiSpecification(
            @JsonProperty("projectName") String projectName,
            @JsonProperty("basePackage") String basePackage,
            @JsonProperty("entities") List<EntityDefinition> entities,
            @JsonProperty("suggestions") List<String> suggestions,
            @JsonProperty("securityHint") String securityHint
    ) {
        this.projectName = Objects.requireNonNull(projectName, "projectName");
        this.basePackage = Objects.requireNonNull(basePackage, "basePackage");
        this.entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        this.suggestions = List.copyOf(Objects.requireNonNull(suggestions, "suggestions"));
        this.securityHint = securityHint == null ? "none" : securityHint;
    }

    /** Backward-compatible constructor without securityHint. */
    public ApiSpecification(String projectName, String basePackage, List<EntityDefinition> entities, List<String> suggestions) {
        this(projectName, basePackage, entities, suggestions, "none");
    }
}
