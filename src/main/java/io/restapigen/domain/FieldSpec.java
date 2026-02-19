package io.restapigen.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class FieldSpec {
    public final String name;
    public final String type;
    public final List<String> validation;
    public final boolean unique;
    public final boolean nullable;

    @JsonCreator
    public FieldSpec(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("validation") List<String> validation,
            @JsonProperty("unique") boolean unique,
            @JsonProperty("nullable") boolean nullable
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.validation = List.copyOf(Objects.requireNonNull(validation, "validation"));
        this.unique = unique;
        this.nullable = nullable;
    }
}
