package io.restapigen.core.parser;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.domain.ApiSpecification;

public interface PromptParser {
    ApiSpecification parse(String prompt, GenerationConfig config);
}
