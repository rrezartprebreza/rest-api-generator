package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class EntityGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "entity-generator";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<GeneratedFile> generate(ApiSpecification specification, PluginContext context) {
        List<GeneratedFile> out = new ArrayList<>();
        String basePackage = context.config().project().basePackage();
        String javaBase = "src/main/java/" + context.basePackagePath();
        for (EntityDefinition definition : specification.entities) {
            String className = definition.entity.name + context.config().standards().naming().entitySuffix();
            Set<String> imports = TemplateSupport.collectImports(definition.entity.fields);
            String content = context.templates().render(
                    context.templatePack().templatePath("entity.java.tpl"),
                    Map.of(
                            "basePackage", basePackage,
                            "entityName", definition.entity.name,
                            "className", className,
                            "imports", imports.stream().map(it -> "import " + it + ";").collect(Collectors.joining("\n")),
                            "fieldsBlock", TemplateSupport.fieldsBlock(definition.entity.fields),
                            "constructorBlock", TemplateSupport.constructorBlock(className, definition.entity.fields),
                            "gettersBlock", TemplateSupport.gettersBlock(definition.entity.fields)
                    )
            );
            out.add(new GeneratedFile(javaBase + "/entity/" + className + ".java", content));
        }
        return out;
    }
}
