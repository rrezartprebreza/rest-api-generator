package io.restapigen.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class ApiSpec {
    public final String resourcePath;
    public final boolean crud;
    public final boolean pagination;
    public final boolean sorting;

    @JsonCreator
    public ApiSpec(
            @JsonProperty("resourcePath") String resourcePath,
            @JsonProperty("crud") boolean crud,
            @JsonProperty("pagination") boolean pagination,
            @JsonProperty("sorting") boolean sorting
    ) {
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
        this.crud = crud;
        this.pagination = pagination;
        this.sorting = sorting;
    }
}
