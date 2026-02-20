package io.restapigen.codegen;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.core.orchestrator.GenerationOrchestrator;
import io.restapigen.core.plugin.PluginLoader;
import io.restapigen.core.validator.SpecValidator;
import io.restapigen.domain.ApiSpecification;

import java.io.IOException;

public final class CodeGenerator {
    private final SpecValidator validator;
    private final PluginLoader pluginLoader;

    public CodeGenerator() {
        this.validator = new SpecValidator();
        this.pluginLoader = new PluginLoader();
    }

    public byte[] generateZip(ApiSpecification spec) throws IOException {
        return generateZip(spec, GenerationConfig.defaults());
    }

    public byte[] generateZip(ApiSpecification spec, GenerationConfig config) throws IOException {
        GenerationConfig effective = config == null ? GenerationConfig.defaults() : config;
        GenerationOrchestrator orchestrator = new GenerationOrchestrator(validator, pluginLoader.load(effective));
        return orchestrator.generateZip(spec, effective);
    }
}
