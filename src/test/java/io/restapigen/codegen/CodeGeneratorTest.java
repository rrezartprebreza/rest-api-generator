package io.restapigen.codegen;

import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.ApiSpec;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.domain.EntitySpec;
import io.restapigen.domain.FieldSpec;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGeneratorTest {
    @Test
    void generatesZipArtifactContainingProjectSkeleton() throws IOException {
        List<FieldSpec> fields = List.of(
                new FieldSpec("name", "String", List.of("NotBlank"), false, false),
                new FieldSpec("price", "BigDecimal", List.of(), false, false)
        );
        ApiSpecification spec = new ApiSpecification(
                "products-api",
                "com.example.generated",
                List.of(new EntityDefinition(
                        new EntitySpec("Product", "products", "Long", fields),
                        new ApiSpec("/api/products", true, true, true)
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
        assertTrue(entries.contains("src/main/java/com/example/generated/dto/ProductDto.java"));
        assertTrue(readme != null);
        assertTrue(readme.contains("Generated REST API entries"));
        assertTrue(readme.contains("Resource path: /api/products"));
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
}
