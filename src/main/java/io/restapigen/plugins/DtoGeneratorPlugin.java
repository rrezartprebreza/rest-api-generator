package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.FieldSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DtoGeneratorPlugin implements GeneratorPlugin {
    @Override
    public String getName() {
        return "dto-generator";
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
        String dtoSuffix = context.config().standards().naming().dtoSuffix();
        for (EntityDefinition definition : specification.entities) {
            String className = definition.entity.name + dtoSuffix;
            StringBuilder body = new StringBuilder();
            for (FieldSpec field : definition.entity.fields) {
                for (String validation : field.validation) {
                    body.append("    @").append(validationToAnnotation(validation)).append("\n");
                }
                body.append("    private ").append(field.type).append(" ").append(field.name).append(";\n\n");
            }
            body.append(TemplateSupport.constructorBlock(className, definition.entity.fields));
            body.append(TemplateSupport.gettersBlock(definition.entity.fields));
            String content = context.templates().render(
                    context.templatePack().templatePath("dto.java.tpl"),
                    Map.of(
                            "basePackage", basePackage,
                            "className", className,
                            "fieldsBlock", body.toString(),
                            "constructorBlock", "",
                            "gettersBlock", ""
                    )
            );
            out.add(new GeneratedFile(javaBase + "/dto/" + className + ".java", content));
        }
        return out;
    }

    private String validationToAnnotation(String token) {
        if (token.startsWith("Size:")) {
            String[] parts = token.split(":");
            if (parts.length == 3) {
                return "Size(min = " + parts[1] + ", max = " + parts[2] + ")";
            }
        }
        return token;
    }
}
