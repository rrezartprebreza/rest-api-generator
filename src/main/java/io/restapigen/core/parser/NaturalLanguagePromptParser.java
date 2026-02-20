package io.restapigen.core.parser;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.generator.NaturalLanguageSpecGenerator;

import java.util.ArrayList;
import java.util.List;

public final class NaturalLanguagePromptParser implements PromptParser {
    private final NaturalLanguageSpecGenerator delegate = new NaturalLanguageSpecGenerator();

    @Override
    public ApiSpecification parse(String prompt, GenerationConfig config) {
        ApiSpecification parsed = delegate.generate(prompt);
        List<EntityDefinition> entities = new ArrayList<>(parsed.entities);
        String projectName = config.project().name();
        String basePackage = config.project().basePackage();
        if (projectName == null || projectName.isBlank()) {
            projectName = parsed.projectName;
        }
        if (basePackage == null || basePackage.isBlank()) {
            basePackage = parsed.basePackage;
        }
        return new ApiSpecification(projectName, basePackage, entities, parsed.suggestions);
    }
}
