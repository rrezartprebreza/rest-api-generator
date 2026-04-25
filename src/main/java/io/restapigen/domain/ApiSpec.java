package io.restapigen.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

public final class ApiSpec {
    public final String resourcePath;
    public final boolean crud;
    public final boolean pagination;
    public final boolean sorting;
    /** Custom endpoint names parsed from "include login, logout, register" DSL. */
    public final List<String> customEndpoints;

    @JsonCreator
    public ApiSpec(
            @JsonProperty("resourcePath") String resourcePath,
            @JsonProperty("crud") boolean crud,
            @JsonProperty("pagination") boolean pagination,
            @JsonProperty("sorting") boolean sorting,
            @JsonProperty("customEndpoints") List<String> customEndpoints
    ) {
        this.resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
        this.crud = crud;
        this.pagination = pagination;
        this.sorting = sorting;
        this.customEndpoints = customEndpoints == null ? List.of() : List.copyOf(customEndpoints);
    }

    /** Backward-compatible constructor without customEndpoints. */
    public ApiSpec(String resourcePath, boolean crud, boolean pagination, boolean sorting) {
        this(resourcePath, crud, pagination, sorting, List.of());
    }
}
