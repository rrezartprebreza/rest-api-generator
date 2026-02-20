package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratorPlugin;

import java.util.List;

public final class BuiltInPlugins {
    private BuiltInPlugins() {
    }

    public static List<GeneratorPlugin> all() {
        return List.of(
                new ProjectReadmeGeneratorPlugin(),
                new EntityGeneratorPlugin(),
                new DtoGeneratorPlugin(),
                new RepositoryGeneratorPlugin(),
                new ServiceGeneratorPlugin(),
                new ControllerGeneratorPlugin(),
                new TestGeneratorPlugin(),
                new MigrationGeneratorPlugin(),
                new DocumentationGeneratorPlugin(),
                new SecurityGeneratorPlugin()
        );
    }
}
