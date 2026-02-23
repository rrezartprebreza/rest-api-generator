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

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String controller = null;
        String service = null;
        String repository = null;
        String mapper = null;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                if ("README.md".equals(entry.getName())) {
                    readme = readAll(zis);
                } else if ("src/main/java/com/example/generated/controller/ProductController.java".equals(entry.getName())) {
                    controller = readAll(zis);
                } else if ("src/main/java/com/example/generated/service/ProductService.java".equals(entry.getName())) {
                    service = readAll(zis);
                } else if ("src/main/java/com/example/generated/repository/ProductRepository.java".equals(entry.getName())) {
                    repository = readAll(zis);
                } else if ("src/main/java/com/example/generated/mapper/ProductMapper.java".equals(entry.getName())) {
                    mapper = readAll(zis);
                }
                zis.closeEntry();
            }
        }

        assertTrue(entries.contains("README.md"));
        assertTrue(entries.contains("build.gradle"));
        assertTrue(entries.contains("settings.gradle"));
        assertTrue(entries.contains("src/main/resources/application.yml"));
        assertTrue(entries.contains("src/main/java/com/example/generated/ProductsApiApplication.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/entity/Product.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/repository/ProductRepository.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/controller/ProductController.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/service/ProductService.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/dto/ProductDTO.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/mapper/ProductMapper.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/error/ErrorResponse.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/error/ResourceNotFoundException.java"));
        assertTrue(entries.contains("src/main/java/com/example/generated/error/GlobalExceptionHandler.java"));
        assertTrue(entries.contains("src/test/java/com/example/generated/integration/ProductIntegrationTest.java"));
        assertTrue(entries.contains("src/main/resources/openapi.yaml"));
        assertTrue(entries.contains("Dockerfile"));
        assertTrue(entries.contains("docker-compose.yml"));
        assertTrue(readme != null);
        assertTrue(readme.contains("Generated REST API entries"));
        assertTrue(readme.contains("Resource path: /api/products"));
        assertTrue(controller != null && controller.contains("@GetMapping(\"/search\")"));
        assertTrue(service != null && service.contains("mapper.toEntity(dto)"));
        assertTrue(repository != null && repository.contains("extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product>"));
        assertTrue(mapper != null && mapper.contains("@Mapper(componentModel = \"spring\")"));
        Map<String, String> zipFiles = readZipFiles(zip);
        assertNoTemplatePlaceholdersInJavaSources(zipFiles);
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
        assertNoTemplatePlaceholdersInJavaSources(zipFiles);
    }

    @Test
    void generatesJpaRelationshipAnnotationsInEntity() throws IOException {
        ApiSpecification spec = new ApiSpecification(
                "shop-api",
                "com.example.generated",
                List.of(
                        new EntityDefinition(
                                new EntitySpec("Category", "categories", "Long", List.of()),
                                new ApiSpec("/api/categories", true, true, true),
                                List.of(new RelationshipSpec("OneToMany", "Product", "productList"))
                        ),
                        new EntityDefinition(
                                new EntitySpec("Product", "products", "Long", List.of()),
                                new ApiSpec("/api/products", true, true, true),
                                List.of(
                                        new RelationshipSpec("ManyToOne", "Category", "category"),
                                        new RelationshipSpec("ManyToMany", "Tag", "tagList")
                                )
                        ),
                        new EntityDefinition(
                                new EntitySpec("Tag", "tags", "Long", List.of()),
                                new ApiSpec("/api/tags", true, true, true),
                                List.of()
                        )
                ),
                List.of()
        );

        byte[] zip = new CodeGenerator().generateZip(spec);
        Map<String, String> zipFiles = readZipFiles(zip);
        String productEntity = zipFiles.get("src/main/java/com/example/generated/entity/Product.java");

        assertTrue(productEntity != null);
        assertTrue(productEntity.contains("@ManyToOne"));
        assertTrue(productEntity.contains("@JoinColumn(name = \"category_id\")"));
        assertTrue(productEntity.contains("@ManyToMany"));
        assertTrue(productEntity.contains("@JoinTable(name = \"product_tag\""));
        assertNoTemplatePlaceholdersInJavaSources(zipFiles);
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
        assertNoTemplatePlaceholdersInJavaSources(zipFiles);
    }

    @Test
    void generatesDtoWithMappedValidationAnnotations() throws IOException {
        ApiSpecification spec = new ApiSpecification(
                "users-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("User", "users", "Long", List.of(
                                new FieldSpec("email", "String", List.of("NotBlank", "Email"), true, false, null, null, "email", false, List.of(), null, null),
                                new FieldSpec("age", "Integer", List.of("Min:18", "Max:120"), false, false, 18, 120, null, false, List.of(), null, null),
                                new FieldSpec("price", "BigDecimal", List.of("DecimalMin:0", "DecimalMax:500"), false, false, 0, 500, null, false, List.of(), null, null),
                                new FieldSpec("status", "String", List.of("OneOf:ACTIVE|INACTIVE"), false, false, null, null, null, false, List.of("ACTIVE", "INACTIVE"), null, null)
                        )),
                        new ApiSpec("/api/users", true, true, true),
                        List.of()
                )),
                List.of()
        );

        byte[] zip = new CodeGenerator().generateZip(spec);
        Map<String, String> zipFiles = readZipFiles(zip);
        String dto = zipFiles.get("src/main/java/com/example/generated/dto/UserDTO.java");

        assertTrue(dto != null);
        assertTrue(dto.contains("@Email"));
        assertTrue(dto.contains("@Min(18)"));
        assertTrue(dto.contains("@Max(120)"));
        assertTrue(dto.contains("@DecimalMin(\"0\")"));
        assertTrue(dto.contains("@DecimalMax(\"500\")"));
        assertTrue(dto.contains("@Pattern(regexp = \"^(ACTIVE|INACTIVE)$\")"));
        assertNoTemplatePlaceholdersInJavaSources(zipFiles);
    }

    @Test
    void generatesAdvancedFieldTypesAndEnums() throws IOException {
        ApiSpecification spec = new ApiSpecification(
                "products-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", List.of(
                                new FieldSpec("status", "ProductStatus", List.of(), false, false, null, null, null, false, List.of("ACTIVE", "INACTIVE"), null, null),
                                new FieldSpec("metadata", "Map<String,Object>", List.of(), false, true, null, null, null, false, List.of(), null, null),
                                new FieldSpec("tags", "List<String>", List.of(), false, true, null, null, null, false, List.of(), null, null),
                                new FieldSpec("createdAt", "LocalDateTime", List.of(), false, false, null, null, null, false, List.of(), null, null)
                        )),
                        new ApiSpec("/api/products", true, true, true),
                        List.of()
                )),
                List.of()
        );

        byte[] zip = new CodeGenerator().generateZip(spec);
        Map<String, String> zipFiles = readZipFiles(zip);
        String entity = zipFiles.get("src/main/java/com/example/generated/entity/Product.java");
        String dto = zipFiles.get("src/main/java/com/example/generated/dto/ProductDTO.java");
        String generatedEnum = zipFiles.get("src/main/java/com/example/generated/entity/ProductStatus.java");

        assertTrue(entity != null);
        assertTrue(entity.contains("@Enumerated(EnumType.STRING)"));
        assertTrue(entity.contains("@ElementCollection"));
        assertTrue(entity.contains("private Map<String,Object> metadata;"));
        assertTrue(entity.contains("private List<String> tags;"));
        assertTrue(entity.contains("private ProductStatus status;"));

        assertTrue(dto != null);
        assertTrue(dto.contains("import java.time.LocalDateTime;"));
        assertTrue(dto.contains("import java.util.List;"));
        assertTrue(dto.contains("import java.util.Map;"));
        assertTrue(dto.contains("private Map<String,Object> metadata;"));
        assertTrue(dto.contains("private List<String> tags;"));

        assertTrue(generatedEnum != null);
        assertTrue(generatedEnum.contains("public enum ProductStatus"));
        assertTrue(generatedEnum.contains("ACTIVE"));
        assertTrue(generatedEnum.contains("INACTIVE"));
        assertNoTemplatePlaceholdersInJavaSources(zipFiles);
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

    private void assertNoTemplatePlaceholdersInJavaSources(Map<String, String> zipFiles) {
        zipFiles.forEach((path, content) -> {
            if (path.endsWith(".java")) {
                assertFalse(
                        content.contains("${"),
                        () -> "Unresolved template placeholder found in " + path
                );
            }
        });
    }
}
