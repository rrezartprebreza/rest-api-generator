package io.restapigen.core.plugin;

import io.restapigen.core.config.GenerationConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginLoaderTest {

    @Test
    void loadsBuiltInPluginsByDefault() {
        PluginLoader loader = new PluginLoader();
        List<GeneratorPlugin> plugins = loader.load(GenerationConfig.defaults());

        List<String> names = plugins.stream().map(GeneratorPlugin::getName).toList();
        assertTrue(names.contains("entity-generator"));
        assertTrue(names.contains("controller-generator"));
        assertTrue(names.contains("project-readme-generator"));
    }

    @Test
    void loadsPluginsFromConfiguredClassNames() {
        GenerationConfig defaults = GenerationConfig.defaults();
        List<String> enabled = new ArrayList<>(defaults.plugins().enabled());
        enabled.add("fixture-plugin");

        GenerationConfig config = new GenerationConfig(
                defaults.project(),
                defaults.standards(),
                defaults.features(),
                new GenerationConfig.PluginsConfig(
                        enabled,
                        defaults.plugins().disabled(),
                        defaults.plugins().externalDirectories(),
                        List.of("io.restapigen.core.plugin.fixtures.ClassNameLoadedTestPlugin")
                )
        );

        PluginLoader loader = new PluginLoader();
        List<GeneratorPlugin> plugins = loader.load(config);

        List<String> names = plugins.stream().map(GeneratorPlugin::getName).toList();
        assertTrue(names.contains("fixture-plugin"));
    }
}
