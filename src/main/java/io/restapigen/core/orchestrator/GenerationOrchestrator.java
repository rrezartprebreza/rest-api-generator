package io.restapigen.core.orchestrator;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.core.plugin.GeneratedFile;
import io.restapigen.core.plugin.GeneratorPlugin;
import io.restapigen.core.plugin.PluginContext;
import io.restapigen.core.template.TemplateEngine;
import io.restapigen.core.template.TemplatePack;
import io.restapigen.core.validator.SpecValidator;
import io.restapigen.domain.ApiSpecification;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class GenerationOrchestrator {
    private final SpecValidator validator;
    private final Map<String, GeneratorPlugin> plugins;

    public GenerationOrchestrator(SpecValidator validator, List<GeneratorPlugin> pluginList) {
        this.validator = validator;
        this.plugins = new LinkedHashMap<>();
        for (GeneratorPlugin plugin : pluginList) {
            this.plugins.put(plugin.getName(), plugin);
        }
    }

    public List<GeneratedFile> generate(ApiSpecification specification, GenerationConfig config) {
        validator.validate(specification);
        PluginContext context = new PluginContext(
                config,
                config.project().basePackage().replace('.', '/'),
                new TemplateEngine(),
                TemplatePack.fromName(config.project().templatePack())
        );

        List<GeneratorPlugin> ordered = orderPlugins(plugins.values().stream()
                .filter(plugin -> config.plugins().isEnabled(plugin.getName()))
                .sorted(Comparator.comparing(GeneratorPlugin::getName))
                .toList());

        List<GeneratedFile> generated = new ArrayList<>();
        for (GeneratorPlugin plugin : ordered) {
            plugin.initialize(context);
            plugin.validate(specification);
            generated.addAll(plugin.generate(specification, context));
        }
        return generated;
    }

    private List<GeneratorPlugin> orderPlugins(List<GeneratorPlugin> selected) {
        Map<String, GeneratorPlugin> byName = new HashMap<>();
        for (GeneratorPlugin plugin : selected) {
            byName.put(plugin.getName(), plugin);
        }
        List<GeneratorPlugin> ordered = new ArrayList<>();
        Set<String> visiting = new java.util.HashSet<>();
        Set<String> visited = new java.util.HashSet<>();
        for (GeneratorPlugin plugin : selected) {
            visit(plugin, byName, visiting, visited, ordered);
        }
        return ordered;
    }

    private void visit(
            GeneratorPlugin plugin,
            Map<String, GeneratorPlugin> byName,
            Set<String> visiting,
            Set<String> visited,
            List<GeneratorPlugin> ordered
    ) {
        String name = plugin.getName();
        if (visited.contains(name)) {
            return;
        }
        if (!visiting.add(name)) {
            throw new IllegalStateException("Plugin dependency cycle detected at " + name);
        }
        for (String dependency : plugin.getDependencies()) {
            GeneratorPlugin dep = byName.get(dependency);
            if (dep == null) {
                throw new IllegalStateException("Missing plugin dependency: " + name + " requires " + dependency);
            }
            visit(dep, byName, visiting, visited, ordered);
        }
        visiting.remove(name);
        visited.add(name);
        ordered.add(plugin);
    }

    public byte[] generateZip(ApiSpecification specification, GenerationConfig config) throws IOException {
        List<GeneratedFile> files = generate(specification, config);
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (GeneratedFile file : files) {
                ZipEntry entry = new ZipEntry(file.path());
                zip.putNextEntry(entry);
                zip.write(file.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return buffer.toByteArray();
        }
    }
}
