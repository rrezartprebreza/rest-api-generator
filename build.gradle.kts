plugins {
    id("java")
    id("application")
}

group = "io.restapigen"
version = "1.0.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // JUnit 5
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // AssertJ for fluent test assertions
    testImplementation("org.assertj:assertj-core:3.27.7")

    // Mockito
    testImplementation("org.mockito:mockito-junit-jupiter:5.23.0")

    // Jackson — core + YAML + Java 8 time types
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.2")

    // CLI
    implementation("info.picocli:picocli:4.7.7")
}

application {
    mainClass.set("io.restapigen.cli.Main")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("rest-api-generator.properties") {
        expand("appVersion" to project.version.toString())
    }
}

// Fat JAR — makes `java -jar` work without the install step
tasks.jar {
    manifest {
        attributes["Main-Class"] = "io.restapigen.cli.Main"
        attributes["Implementation-Version"] = project.version
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
