package io.restapigen.core.parser;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;
import io.restapigen.domain.EntityDefinition;
import io.restapigen.generator.ProjectNaming;
import io.restapigen.generator.NaturalLanguageSpecGenerator;

import java.util.ArrayList;
import java.util.List;

public final class NaturalLanguagePromptParser implements PromptParser {
    private final NaturalLanguageSpecGenerator delegate = new NaturalLanguageSpecGenerator();

    @Override
    public ApiSpecification parse(String prompt, GenerationConfig config) {
        ApiSpecification parsed = delegate.generate(prompt);
        return applyProjectIdentity(prompt, parsed, config);
    }

    public static ApiSpecification applyProjectIdentity(String originalPrompt, ApiSpecification parsed, GenerationConfig config) {
        List<EntityDefinition> entities = new ArrayList<>(parsed.entities);
        String projectName = config.hasExplicitProjectName()
                ? config.project().name()
                : ProjectNaming.inferProjectName(originalPrompt, entities);
        String basePackage = config.hasExplicitBasePackage()
                ? config.project().basePackage()
                : ProjectNaming.inferBasePackage(projectName);
        return new ApiSpecification(projectName, basePackage, entities, parsed.suggestions, parsed.securityHint);
    }
}
