package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TestGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "test-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<String> getDependencies() {
        return List.of("service-generator");
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        boolean includeUnitTests = context.config().standards().testing().includeUnitTests();
        boolean includeIntegrationTests = context.config().standards().testing().includeIntegrationTests();
        if (!includeUnitTests && !includeIntegrationTests) {
            return List.of();
        }

        List<GeneratedFile> out = new ArrayList<>();
        String basePackage = context.config().project().basePackage();
        String testBase = "src/test/java/" + context.basePackagePath();
        String serviceSuffix = context.config().standards().naming().serviceSuffix();
        for (EntityDefinition definition : specification.entities) {
            String entityName = definition.entity.name;
            if (includeUnitTests) {
                String className = entityName + serviceSuffix + "Test";
                String content = context.templates().render(
                        context.templatePack().templatePath("test.java.tpl"),
                        Map.of(
                                "basePackage", basePackage,
                                "className", className,
                                "entityName", entityName
                        )
                );
                out.add(new GeneratedFile(testBase + "/service/" + className + ".java", content));
            }

            if (includeIntegrationTests) {
                String className = entityName + "IntegrationTest";
                String content = context.templates().render(
                        context.templatePack().templatePath("integration-test.java.tpl"),
                        Map.of(
                                "basePackage", basePackage,
                                "className", className,
                                "entityName", entityName
                        )
                );
                out.add(new GeneratedFile(testBase + "/integration/" + className + ".java", content));
            }
        }
        return out;
    }
}
