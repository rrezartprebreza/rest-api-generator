package io.restapigen.core.plugin;

import io.restapigen.core.config.GenerationConfig;
import io.restapigen.plugins.BuiltInPlugins;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class PluginLoader {

    public List<GeneratorPlugin> load(GenerationConfig config) {
        GenerationConfig effective = config == null ? GenerationConfig.defaults() : config;
        Map<String, GeneratorPlugin> byName = new LinkedHashMap<>();

        for (GeneratorPlugin builtIn : BuiltInPlugins.all()) {
            byName.put(builtIn.getName(), builtIn);
        }

        // Plugins bundled on the runtime classpath (SDK use case).
        loadFromServiceLoader(ServiceLoader.load(GeneratorPlugin.class), byName);

        for (String dir : effective.plugins().externalDirectories()) {
            loadFromDirectory(dir, byName);
        }

        for (String className : effective.plugins().externalClassNames()) {
            GeneratorPlugin plugin = instantiate(className);
            byName.put(plugin.getName(), plugin);
        }

        return List.copyOf(byName.values());
    }

    private void loadFromDirectory(String directory, Map<String, GeneratorPlugin> byName) {
        if (directory == null || directory.isBlank()) {
            return;
        }
        Path path = Path.of(directory);
        if (!Files.isDirectory(path)) {
            return;
        }

        List<URL> jarUrls = new ArrayList<>();
        try (var stream = Files.list(path)) {
            stream.filter(entry -> Files.isRegularFile(entry) && entry.toString().endsWith(".jar"))
                    .forEach(entry -> {
                        try {
                            jarUrls.add(entry.toUri().toURL());
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
            return;
        }

        if (jarUrls.isEmpty()) {
            return;
        }

        URLClassLoader classLoader = new URLClassLoader(jarUrls.toArray(URL[]::new), PluginLoader.class.getClassLoader());
        loadFromServiceLoader(ServiceLoader.load(GeneratorPlugin.class, classLoader), byName);
    }

    private void loadFromServiceLoader(ServiceLoader<GeneratorPlugin> loader, Map<String, GeneratorPlugin> byName) {
        for (GeneratorPlugin plugin : loader) {
            byName.put(plugin.getName(), plugin);
        }
    }

    private GeneratorPlugin instantiate(String className) {
        try {
            Class<?> candidate = Class.forName(className);
            if (!GeneratorPlugin.class.isAssignableFrom(candidate)) {
                throw new IllegalArgumentException("Class does not implement GeneratorPlugin: " + className);
            }
            return (GeneratorPlugin) candidate.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot load plugin class: " + className, e);
        }
    }
}
