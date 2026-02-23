package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratorPlugin;

import java.util.List;

public final class BuiltInPlugins {
    private BuiltInPlugins() {
    }

    public static List<GeneratorPlugin> all() {
        return List.of(
                new ProjectScaffoldGeneratorPlugin(),
                new ProjectReadmeGeneratorPlugin(),
                new EntityGeneratorPlugin(),
                new DtoGeneratorPlugin(),
                new MapperGeneratorPlugin(),
                new RepositoryGeneratorPlugin(),
                new ServiceGeneratorPlugin(),
                new ControllerGeneratorPlugin(),
                new ErrorHandlingGeneratorPlugin(),
                new TestGeneratorPlugin(),
                new MigrationGeneratorPlugin(),
                new DocumentationGeneratorPlugin(),
                new DockerGeneratorPlugin(),
                new SecurityGeneratorPlugin()
        );
    }
}
