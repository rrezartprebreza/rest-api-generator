package io.restapigen.core.plugin;

import io.restapigen.domain.ApiSpecification;

import java.util.List;

public interface GeneratorPlugin {
    String getName();

    String getVersion();

    default List<String> getDependencies() {
        return List.of();
    }

    default void initialize(PluginContext context) {
    }

    default void validate(ApiSpecification specification) {
    }

    List<GeneratedFile> generate(ApiSpecification specification, PluginContext context);
}
