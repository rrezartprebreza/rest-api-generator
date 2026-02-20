# REST API Generator

Generate Spring Boot REST API scaffolding from natural language prompts.

This project is designed for teams: developers run the generator and receive a consistent ZIP scaffold that follows shared standards.

## What is implemented now

- Plugin-based generation pipeline (`entity`, `dto`, `repository`, `service`, `controller`, `test`, `migration`, `docs`, `security` placeholder)
- Multi-entity prompt parsing (separate entities with blank lines)
- Relationship parsing (`belongs to`, `has many`, `many-to-many`)
- Strict spec validation (relationship targets, field constraints, naming)
- YAML configuration (`.rest-api-generator.yml`)
- Template-pack support with starter packs and fallback resolution
- HTTP API endpoints:
  - `POST /generator/spec`
  - `POST /generator/code`

## Quick start

1. Build/test:

```bash
./gradlew clean test
```

2. Start server:

```bash
./gradlew run --args="serve --port 8080"
```

3. Generate spec from prompt:

```bash
curl -X POST http://localhost:8080/generator/spec \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Create an API for Product with name, price, stock\n\nCreate an API for Employee with firstName, lastName, email"}' \
  -o spec.json
```

4. Generate ZIP from spec:

```bash
curl -X POST http://localhost:8080/generator/code \
  -H "Content-Type: application/json" \
  --data-binary @spec.json \
  -o scaffold.zip
```

The generator returns only the ZIP response artifact for generated code. It does not write generated source into this generator project's source tree.

5. Try full example prompts:

```bash
./gradlew run --args="generate --file examples/prompts/ecommerce.txt --pretty"
./gradlew run --args="generate --file examples/prompts/blog.txt --pretty"
```

## CLI commands

```bash
./gradlew run --args="generate --prompt 'Create API for User with email, password' --pretty"
./gradlew run --args="generate --file ./prompt.txt --pretty"
./gradlew run --args="init --config .rest-api-generator.yml --template spring-boot-3-standard"
./gradlew run --args="validate --config .rest-api-generator.yml"
./gradlew run --args="templates list"
./gradlew run --args="plugins list --config .rest-api-generator.yml"
./gradlew run --args="serve --port 8080"
```

Legacy flags still work:
- `--user-request`
- `--input`
- `--serve`
- `--init-config`
- `--validate-config`

## Config and schema

- Example config model: `src/main/java/io/restapigen/core/config/GenerationConfig.java`
- Selected template pack can be set with `project.templatePack` or CLI `--template`.
- Config JSON schema: `schemas/rest-api-generator-config.schema.json`
- Spec JSON schema: `schemas/api-specification.schema.json`

Plugin extension fields:
- `plugins.externalDirectories`: directories scanned for plugin JARs (default `plugins`)
- `plugins.externalClassNames`: explicit class names to instantiate as plugins

## Template packs

Pack descriptors:
- `templates/packs/spring-boot-3-standard.yml`
- `templates/packs/microservices-pattern.yml`
- `templates/packs/ddd-layered.yml`

Runtime templates:
- `src/main/resources/templates/spring-boot-3-standard/`

## Architecture and roadmap

- Phase-1 implementation details: `docs/PHASE1-IMPLEMENTATION.md`
- Phase-2 implementation details: `docs/PHASE2-IMPLEMENTATION.md`
- Phase-3 implementation details: `docs/PHASE3-IMPLEMENTATION.md`
- Phase-4 implementation details: `docs/PHASE4-IMPLEMENTATION.md`
- Publish checklist: `docs/PUBLISH-CHECKLIST.md`
- Enterprise blueprint and deliverables mapping: `docs/ENTERPRISE-BLUEPRINT.md`
