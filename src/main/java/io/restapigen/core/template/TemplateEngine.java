package io.restapigen.core.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class TemplateEngine {
    public String render(String templatePath, Map<String, String> values) {
        String template = readTemplate(templatePath);
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private String readTemplate(String templatePath) {
        InputStream stream = TemplateEngine.class.getClassLoader().getResourceAsStream(templatePath);
        if (stream == null) {
            String fallback = templatePath.replaceFirst("^templates/[^/]+/", "templates/spring-boot-3-standard/");
            stream = TemplateEngine.class.getClassLoader().getResourceAsStream(fallback);
            if (stream == null) {
                throw new IllegalArgumentException("Template not found: " + templatePath);
            }
        }
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read template: " + templatePath, e);
        }
    }
}
