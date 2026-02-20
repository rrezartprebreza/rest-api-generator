package io.restapigen.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class RelationshipSpec {
    public final String type;
    public final String target;
    public final String fieldName;

    @JsonCreator
    public RelationshipSpec(
            @JsonProperty("type") String type,
            @JsonProperty("target") String target,
            @JsonProperty("fieldName") String fieldName
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.target = Objects.requireNonNull(target, "target");
        this.fieldName = Objects.requireNonNull(fieldName, "fieldName");
    }
}
