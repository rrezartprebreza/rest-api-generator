package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;

import java.util.List;
import java.util.Map;

public final class SecurityGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "security-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        if (!context.config().standards().security().enabled()) {
            return List.of();
        }
        String basePackage = context.config().project().basePackage();
        String javaBase = "src/main/java/" + context.basePackagePath();
        String content = context.templates().render(
                context.templatePack().templatePath("security.java.tpl"),
                Map.of(
                        "basePackage", basePackage,
                        "securityType", context.config().standards().security().type()
                )
        );
        return List.of(new GeneratedFile(javaBase + "/security/SecurityConfig.java", content));
    }
}
