# REST API Generator

Generate Spring Boot REST API scaffolding from natural language prompts.

This project is designed for teams: developers run the generator and receive a consistent ZIP scaffold that follows shared standards.

## What Developers Can Do

- Write natural-language prompts for single or multi-entity APIs.
- Define relationships like `belongs to`, `has many`, and `many-to-many`.
- Generate a JSON specification (`/generator/spec`) and then a runnable ZIP scaffold (`/generator/code`).
- Apply team standards via `.rest-api-generator.yml` (naming, layering, testing, database, migrations, plugins).
- Choose template packs (`spring-boot-3-standard`, `microservices-pattern`, `ddd-layered`).
- Extend generation with custom plugins (`plugins.externalDirectories`, `plugins.externalClassNames`).
- Validate quality with strict spec checks and CI (`.github/workflows/ci.yml`).

## What is implemented now

- Plugin-based generation pipeline (`entity`, `dto`, `repository`, `service`, `controller`, `test`, `migration`, `docs`, `security` placeholder)
- Multi-entity prompt parsing (separate entities with blank lines)
- Relationship parsing (`belongs to`, `has many`, `many-to-many`)
- JPA relationship scaffolding in entities (`@ManyToOne`, `@OneToMany`, `@ManyToMany`, join table hints)
- Strict spec validation (relationship targets, field constraints, naming)
- Enhanced validation extraction (`min/max`, `valid email`, enum constraints) with DTO annotation mapping
- Advanced field type support (`enum` -> generated enum class, `boolean`, `decimal`/`BigDecimal`, `date`/`timestamp`, `json`, `list`/`array`)
- Global error-handling scaffold generation (`GlobalExceptionHandler`, `ErrorResponse`, `ResourceNotFoundException`)
- Pagination/sorting/filtering method scaffolding in repository/service/controller layers
- MapStruct mapper generation (`<Entity>Mapper`) for DTO <-> entity conversion
- Spring Data repository scaffolding (`JpaRepository` + `JpaSpecificationExecutor`) with pageable query flow
- YAML configuration (`.rest-api-generator.yml`)
- Template-pack support with starter packs and fallback resolution
- OpenAPI export command (`openapi`) for prompt-to-spec docs output
- Docker scaffold generation (`Dockerfile`, `docker-compose.yml`)
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

### Advanced Multi-Entity Example (Relationships + Validations)

Generate a richer spec with relationships and constraints:

```bash
curl -X POST http://localhost:8080/generator/spec \
  -H "Content-Type: application/json" \
  -d '{
    "prompt":"Create an API for Category with:
- name (string, required, unique)

Create an API for Product with:
- name (string, required, min 2, max 100)
- price (decimal, required, min 0, max 10000)
- status (enum: ACTIVE, INACTIVE)
- ownerEmail (string, required, valid email)
- active (boolean)
- createdAt (timestamp)
- metadata (json)
- tags (list<string>)
- belongs to Category
- has many Tag (many-to-many)

Create an API for Tag with:
- name (string, required, unique)"
  }' \
  -o spec.json
```

Generate code ZIP from that spec:

```bash
curl -X POST http://localhost:8080/generator/code \
  -H "Content-Type: application/json" \
  --data-binary @spec.json \
  -o scaffold.zip
```

Quick verification:

```bash
unzip -p scaffold.zip src/main/java/com/example/generated/entity/Product.java
unzip -p scaffold.zip src/main/java/com/example/generated/dto/ProductDTO.java
unzip -p scaffold.zip src/main/java/com/example/generated/error/GlobalExceptionHandler.java
```

5. Try full example prompts:

```bash
./gradlew run --args="generate --file examples/prompts/ecommerce.txt --pretty"
./gradlew run --args="generate --file examples/prompts/blog.txt --pretty"
```

## CLI commands

```bash
./gradlew run --args="generate --prompt 'Create API for User with email, password' --pretty"
./gradlew run --args="generate --file ./prompt.txt --pretty"
./gradlew run --args="openapi --prompt 'Create API for User with email, password'"
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
- `features.dockerArtifacts`: include Docker artifacts in generated ZIP output

## Template packs

Pack descriptors:
- `templates/packs/spring-boot-3-standard.yml`
- `templates/packs/microservices-pattern.yml`
- `templates/packs/ddd-layered.yml`

Runtime templates:
- `src/main/resources/templates/spring-boot-3-standard/`
