package io.restapigen.codegen;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import io.restapigen.domain.FieldSpec;
import io.restapigen.domain.RelationshipSpec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGeneratorTest {
    @Test
    void generatesZipArtifactContainingProjectSkeleton() throws IOException {
        List<FieldSpec> fields = List.of(
                new FieldSpec("name", "String", List.of("NotBlank"), false, false, null, null, null, false, List.of(), null, null),
                new FieldSpec("price", "BigDecimal", List.of(), false, false, null, null, null, false, List.of(), null, null)
        );
        ApiSpecification spec = new ApiSpecification(
                "products-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", fields),
                        new ApiSpec("/api/products", true, true, true),
                        List.of()
                )),
                List.of()
        );

        byte[] zip = new CodeGenerator().generateZip(spec);
        assertTrue(zip.length > 0);

        Set<String> entries = new HashSet<>();
        String readme = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                if ("README.md".equals(entry.getName())) {
                    readme = readAll(zis);
                }
                zis.closeEntry();
            }
        }

        assertTrue(entries.contains("README.md"));
        assertTrue(entries.contains("src/main/java/com/example/generated/entity/Product.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/repository/ProductRepository.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/controller/ProductController.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/service/ProductService.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/dto/ProductDTO.java"));
        assertTrue(entries.contains("src/test/java/com/example/generated/integration/ProductIntegrationTest.java"));
        assertTrue(entries.contains("src/main/resources/openapi.yaml"));
        assertTrue(readme != null);
        assertTrue(readme.contains("Generated REST API entries"));
        assertTrue(readme.contains("Resource path: /api/products"));
    }

    @Test
    void generatesRelationshipAwareMigrations() throws IOException {
        ApiSpecification spec = new ApiSpecification(
                "shop-api",
                "com.example.generated",
                List.of(
                        new EntityDefinition(
                                new EntitySpec("Category", "categories", "Long", List.of(
                                        new FieldSpec("name", "String", List.of("NotBlank"), true, false, null, null, null, false, List.of(), null, null)
                                )),
                                new ApiSpec("/api/categories", true, true, true),
                                List.of()
                        ),
                        new EntityDefinition(
                                new EntitySpec("Product", "products", "Long", List.of(
                                        new FieldSpec("name", "String", List.of("NotBlank"), false, false, null, null, null, false, List.of(), null, null)
                                )),
                                new ApiSpec("/api/products", true, true, true),
                                List.of(new RelationshipSpec("ManyToOne", "Category", "category"))
                        )
                ),
                List.of()
        );

        byte[] zip = new CodeGenerator().generateZip(spec);
        Map<String, String> zipFiles = readZipFiles(zip);
        String migration = zipFiles.get("src/main/resources/db/migration/V2__create_products_table.sql");

        assertTrue(migration != null);
        assertTrue(migration.contains("category_id BIGINT"));
        assertTrue(migration.contains("REFERENCES categories(id)"));
    }

    @Test
    void includesExternalClassPluginOutputWhenEnabled() throws IOException {
        ApiSpecification spec = new ApiSpecification(
                "products-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", List.of()),
                        new ApiSpec("/api/products", true, true, true),
                        List.of()
                )),
                List.of()
        );

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

        byte[] zip = new CodeGenerator().generateZip(spec, config);
        Map<String, String> zipFiles = readZipFiles(zip);
        assertTrue(zipFiles.containsKey("CUSTOM_PLUGIN.txt"));
        assertTrue(zipFiles.get("CUSTOM_PLUGIN.txt").contains("loaded:products-api"));
    }

    private String readAll(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[512];
        int read;
        while ((read = zis.read(tmp)) != -1) {
            buffer.write(tmp, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private Map<String, String> readZipFiles(byte[] zip) throws IOException {
        java.util.LinkedHashMap<String, String> files = new java.util.LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                files.put(entry.getName(), readAll(zis));
                zis.closeEntry();
            }
        }
        return files;
    }
}
