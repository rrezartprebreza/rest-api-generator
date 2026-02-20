package io.restapigen.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class EntityDefinition {
    public final EntitySpec entity;
    public final ApiSpec api;
    public final List<RelationshipSpec> relationships;

    @JsonCreator
    public EntityDefinition(
            @JsonProperty("entity") EntitySpec entity,
            @JsonProperty("api") ApiSpec api,
            @JsonProperty("relationships") List<RelationshipSpec> relationships
    ) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.api = Objects.requireNonNull(api, "api");
        this.relationships = List.copyOf(relationships == null ? List.of() : relationships);
    }
}
