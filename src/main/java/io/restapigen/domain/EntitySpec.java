package io.restapigen.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class EntitySpec {
    public final String name;
    public final String table;
    public final String idType;
    public final List<FieldSpec> fields;

    @JsonCreator
    public EntitySpec(
            @JsonProperty("name") String name,
            @JsonProperty("table") String table,
            @JsonProperty("idType") String idType,
            @JsonProperty("fields") List<FieldSpec> fields
    ) {
        this.name = Objects.requireNonNull(name, "name");
        this.table = Objects.requireNonNull(table, "table");
        this.idType = Objects.requireNonNull(idType, "idType");
        this.fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
    }
}
