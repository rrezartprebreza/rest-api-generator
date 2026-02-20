package io.restapigen.core.template;

import java.util.List;

public record TemplatePack(String name) {
    public String templatePath(String fileName) {
        return "templates/" + name + "/" + fileName;
    }

    public static TemplatePack springBootStandard() {
        return new TemplatePack("spring-boot-3-standard");
    }

    public static TemplatePack fromName(String name) {
        if (name == null || name.isBlank()) {
            return springBootStandard();
        }
        if (availablePacks().contains(name)) {
            return new TemplatePack(name);
        }
        return springBootStandard();
    }

    public static List<String> availablePacks() {
        return List.of(
                "spring-boot-3-standard",
                "microservices-pattern",
                "ddd-layered"
        );
    }
}
