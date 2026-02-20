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
    public final Integer min;
    public final Integer max;
    public final String format;
    public final boolean encrypted;
    public final List<String> enumValues;
    public final String defaultValue;
    public final String calculatedExpression;

    @JsonCreator
    public FieldSpec(
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,
            @JsonProperty("validation") List<String> validation,
            @JsonProperty("unique") boolean unique,
            @JsonProperty("nullable") boolean nullable,
            @JsonProperty("min") Integer min,
            @JsonProperty("max") Integer max,
            @JsonProperty("format") String format,
            @JsonProperty("encrypted") boolean encrypted,
            @JsonProperty("enumValues") List<String> enumValues,
            @JsonProperty("defaultValue") String defaultValue,
            @JsonProperty("calculatedExpression") String calculatedExpression
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.validation = List.copyOf(Objects.requireNonNull(validation, "validation"));
        this.unique = unique;
        this.nullable = nullable;
        this.min = min;
        this.max = max;
        this.format = format;
        this.encrypted = encrypted;
        this.enumValues = List.copyOf(enumValues == null ? List.of() : enumValues);
        this.defaultValue = defaultValue;
        this.calculatedExpression = calculatedExpression;
    }
}
