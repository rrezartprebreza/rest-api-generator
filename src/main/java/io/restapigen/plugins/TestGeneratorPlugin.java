package io.restapigen.plugins;

import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.RelationshipSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
                    Map.ofEntries(
                            Map.entry("basePackage",     basePackage),
                            Map.entry("className",       testClass),
                            Map.entry("entityName",      entityName),
                            Map.entry("entityClass",     entityClass),
                            Map.entry("serviceClass",    serviceClass),
                            Map.entry("repositoryClass", repositoryClass),
                            Map.entry("mapperClass",     mapperClass),
                            Map.entry("dtoClass",        dtoClass),
                            Map.entry("relatedRepositoryImports", relatedRepositoryImports(basePackage, repositorySuffix, definition.relationships)),
                            Map.entry("relatedRepositoryMocks", relatedRepositoryMocks(repositorySuffix, definition.relationships)),
                            Map.entry("relatedDtoDefaults", relatedDtoDefaults(definition.relationships)),
                            Map.entry("idType",          definition.entity.idType),
                            Map.entry("idTypeImport",    idTypeImport(definition.entity.idType)),
                            Map.entry("testExistingId",  testIdLiteral(definition.entity.idType, false)),
                            Map.entry("testMissingId",   testIdLiteral(definition.entity.idType, true))
                    )
            );
            out.add(new GeneratedFile(testBase + "/service/" + testClass + ".java", unitContent));

            // Integration test
            if (context.config().standards().testing().includeIntegrationTests()) {
                String integrationClass = entityName + "IntegrationTest";
                String integrationContent = context.templates().render(
                        context.templatePack().templatePath("integration-test.java.tpl"),
                        Map.ofEntries(
                                Map.entry("basePackage",  basePackage),
                                Map.entry("className",    integrationClass),
                                Map.entry("entityName",   entityName),
                                Map.entry("dtoClass",     dtoClass),
                                Map.entry("resourcePath", definition.api.resourcePath),
                                Map.entry("missingIdPathValue", testPathIdLiteral(definition.entity.idType))
                        )
                );
                out.add(new GeneratedFile(testBase + "/integration/" + integrationClass + ".java", integrationContent));
            }
        }
        return out;
    }

    private String idTypeImport(String idType) {
        Set<String> imports = new LinkedHashSet<>();
        TemplateSupport.addTypeImport(imports, idType);
        return imports.stream()
                .map(value -> "import " + value + ";")
                .collect(Collectors.joining("\n"));
    }

    private String relatedRepositoryImports(String basePackage, String repositorySuffix, List<RelationshipSpec> relationships) {
        return writableRelationships(relationships).stream()
                .map(r -> "import " + basePackage + ".repository." + r.target + repositorySuffix + ";")
                .distinct()
                .collect(Collectors.joining("\n"));
    }

    private String relatedRepositoryMocks(String repositorySuffix, List<RelationshipSpec> relationships) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec relationship : writableRelationships(relationships)) {
            sb.append("\n")
                    .append("    @Mock\n")
                    .append("    private ").append(relationship.target).append(repositorySuffix).append(" ")
                    .append(decapitalize(relationship.target)).append("Repository;\n");
        }
        return sb.toString();
    }

    private String relatedDtoDefaults(List<RelationshipSpec> relationships) {
        StringBuilder sb = new StringBuilder();
        for (RelationshipSpec relationship : writableRelationships(relationships)) {
            String suffix = "ManyToMany".equals(relationship.type) ? "Ids" : "Id";
            sb.append("        lenient().when(dto.get").append(capitalize(relationship.fieldName)).append(suffix)
                    .append("()).thenReturn(null);\n");
        }
        return sb.toString();
    }

    private List<RelationshipSpec> writableRelationships(List<RelationshipSpec> relationships) {
        return relationships.stream()
                .filter(r -> "ManyToOne".equals(r.type) || "OneToOne".equals(r.type) || "ManyToMany".equals(r.type))
                .toList();
    }

    private String testIdLiteral(String idType, boolean missing) {
        return switch (idType) {
            case "UUID" -> missing
                    ? "UUID.fromString(\"00000000-0000-0000-0000-000000000099\")"
                    : "UUID.fromString(\"00000000-0000-0000-0000-000000000001\")";
            case "String" -> missing ? "\"missing-id\"" : "\"existing-id\"";
            case "Integer" -> missing ? "99" : "1";
            default -> missing ? "99L" : "1L";
        };
    }

    private String testPathIdLiteral(String idType) {
        return switch (idType) {
            case "UUID" -> "00000000-0000-0000-0000-000000000099";
            case "String" -> "missing-id";
            case "Integer" -> "99";
            default -> "999999";
        };
    }

    private static String decapitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
