package io.restapigen.core.plugin.fixtures;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.core.plugin.PluginDefinition;
import io.restapigen.domain.ApiSpecification;

import java.util.List;

@PluginDefinition(name = "fixture-plugin", version = "1.0.0", description = "Test plugin loaded by class name")
public final class ClassNameLoadedTestPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "fixture-plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        return List.of(new GeneratedFile("CUSTOM_PLUGIN.txt", "loaded:" + specification.projectName));
    }
}
