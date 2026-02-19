package io.restapigen.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class EntityDefinition {
    public final EntitySpec entity;
    public final ApiSpec api;

    @JsonCreator
    public EntityDefinition(
            @JsonProperty("entity") EntitySpec entity,
            @JsonProperty("api") ApiSpec api
    ) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.api = Objects.requireNonNull(api, "api");
    }
}
