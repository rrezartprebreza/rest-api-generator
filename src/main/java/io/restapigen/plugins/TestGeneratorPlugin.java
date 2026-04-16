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
    @Override public String getName()    { return "test-generator"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        if (!context.config().standards().testing().includeUnitTests()) {
            return List.of();
        }
        List<GeneratedFile> out      = new ArrayList<>();
        String basePackage           = context.config().project().basePackage();
        String testBase              = "src/test/java/" + context.basePackagePath();
        String serviceSuffix         = context.config().standards().naming().serviceSuffix();
        String repositorySuffix      = context.config().standards().naming().repositorySuffix();
        String dtoSuffix             = context.config().standards().naming().dtoSuffix();
        String entitySuffix          = context.config().standards().naming().entitySuffix();

        for (EntityDefinition definition : specification.entities) {
            String entityName      = definition.entity.name;
            String entityClass     = entityName + entitySuffix;
            String serviceClass    = entityName + serviceSuffix;
            String repositoryClass = entityName + repositorySuffix;
            String mapperClass     = entityName + "Mapper";
            String dtoClass        = entityName + dtoSuffix;
            String testClass       = serviceClass + "Test";

            // Unit test
            String unitContent = context.templates().render(
                    context.templatePack().templatePath("test.java.tpl"),
                    Map.of(
                            "basePackage",     basePackage,
                            "className",       testClass,
                            "entityName",      entityClass,
                            "serviceClass",    serviceClass,
                            "repositoryClass", repositoryClass,
                            "mapperClass",     mapperClass,
                            "dtoClass",        dtoClass
                    )
            );
            out.add(new GeneratedFile(testBase + "/service/" + testClass + ".java", unitContent));

            // Integration test
            if (context.config().standards().testing().includeIntegrationTests()) {
                String integrationClass = entityName + "IntegrationTest";
                String integrationContent = context.templates().render(
                        context.templatePack().templatePath("integration-test.java.tpl"),
                        Map.of(
                                "basePackage",  basePackage,
                                "className",    integrationClass,
                                "entityName",   entityName,
                                "dtoClass",     dtoClass,
                                "resourcePath", definition.api.resourcePath
                        )
                );
                out.add(new GeneratedFile(testBase + "/integration/" + integrationClass + ".java", integrationContent));
            }
        }
        return out;
    }
}
