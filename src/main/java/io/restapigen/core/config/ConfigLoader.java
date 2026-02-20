package io.restapigen.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {
    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    public GenerationConfig load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return GenerationConfig.defaults();
        }
        GenerationConfig parsed = mapper.readValue(Files.readString(path), GenerationConfig.class);
        return parsed == null ? GenerationConfig.defaults() : parsed;
    }

    public void writeDefault(Path path) throws IOException {
        writeDefault(path, null);
    }

    public void writeDefault(Path path, String templatePack) throws IOException {
        GenerationConfig defaults = GenerationConfig.defaults();
        if (templatePack != null && !templatePack.isBlank()) {
            defaults = defaults.withTemplatePack(templatePack);
        }
        String yaml = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(defaults);
        Files.writeString(path, yaml);
    }
}
