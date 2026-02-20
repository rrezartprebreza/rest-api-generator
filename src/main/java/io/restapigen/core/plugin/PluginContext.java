package io.restapigen.core.plugin;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.core.template.TemplateEngine;
import io.restapigen.core.template.TemplatePack;

public record PluginContext(GenerationConfig config, String basePackagePath, TemplateEngine templates, TemplatePack templatePack) {
}
