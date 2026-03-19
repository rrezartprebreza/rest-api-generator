# REST API Generator

> Generate production-ready Spring Boot REST APIs from plain English in seconds

[📺 Demo Video](#) | [🚀 Quick Start](#quick-start) | [📚 Examples](#examples) | [💬 Discord](#)

---

## Why Use This?

❌ **Before:**
- Manually create Entity, DTO, Repository, Service, Controller
- Copy-paste boilerplate for each field
- Wire up relationships by hand
- Add validation annotations one-by-one
- Set up error handling
- Configure pagination
- Write MapStruct mappers
- **Time: 2-3 hours per entity**

✅ **After:**
- Complete CRUD API generated
- All layers (Entity, DTO, Service, Controller)
- Relationships configured
- Validation included
- Error handling set up
- Pagination working
- **Time: 30 seconds**

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
- Project scaffold generation (`build.gradle`, `settings.gradle`, `application.yml`, Spring Boot main class)
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
- Optional DB editor in Docker (`Adminer`) for PostgreSQL/MySQL via compose profile `tools`
- HTTP API endpoints:
  - `GET /about`
  - `POST /generator/spec`
  - `POST /generator/code`

## Requirements

- Java 17+ and Gradle available on the machine.
- Generated ZIP projects can be unzipped and opened directly in an IDE (no Spring Initializr step required).

## Quick start

1. Build/test:

```bash
./gradlew clean test
```

2. Generate ZIP directly from a prompt file:

```bash
./gradlew run --args="generate-zip --file examples/prompts/ecommerce.txt --out scaffold.zip"
```

3. Unzip and run generated project:

```bash
unzip scaffold.zip -d generated-api
cd generated-api
./gradlew bootRun
```

### Shell notes (Linux/macOS vs Windows)

- `bash`/`zsh` examples in this README use `\` for multiline commands.
- In Windows PowerShell, use backtick `` ` `` for multiline commands.
- In PowerShell, use `curl.exe` (not `curl`) to avoid alias differences.

## Examples

### Advanced Multi-Entity Example (Relationships + Validations)

Use the provided prompt file (`examples/prompts/ecommerce.txt`) and generate ZIP with one command.

bash/zsh:

```bash
./gradlew run --args="generate-zip --file examples/prompts/ecommerce.txt --out scaffold.zip"
```

If `--out` is omitted, output defaults to `./scaffold.zip`.

Windows PowerShell (including IntelliJ terminal on Windows):

```powershell
./gradlew run --args="generate-zip --file examples/prompts/ecommerce.txt --out scaffold.zip"
```

Windows (`cmd.exe` / PowerShell) with batch wrapper:

```powershell
.\gradlew.bat run --args="generate-zip --file examples/prompts/ecommerce.txt --out scaffold.zip"
```

Alternative API mode (server + endpoints):

```bash
./gradlew run --args="serve --port 8080"
```

```bash
jq -Rs '{prompt:.}' examples/prompts/ecommerce.txt \
| curl -s -X POST http://localhost:8080/generator/spec \
  -H "Content-Type: application/json" \
  --data-binary @- -o spec.json \
&& curl -s -X POST http://localhost:8080/generator/code \
  -H "Content-Type: application/json" \
  --data-binary @spec.json -o scaffold.zip
```

Quick verification:

```bash
unzip -p scaffold.zip src/main/java/com/example/generated/entity/Product.java
unzip -p scaffold.zip src/main/java/com/example/generated/dto/ProductDTO.java
unzip -p scaffold.zip src/main/java/com/example/generated/error/GlobalExceptionHandler.java
```

Run the generated project directly:

```bash
unzip scaffold.zip -d generated-api
cd generated-api
./gradlew bootRun
```

Run generated project with Docker:

```bash
unzip scaffold.zip -d generated-api
cd generated-api

# App + DB
docker compose up

# App + DB + Adminer (DB editor)
docker compose --profile tools up
```

Adminer UI:

- URL: `http://localhost:8081`
- Server: `db`
- PostgreSQL: database `appdb`, user `app`, password `app`
- MySQL: database `appdb`, user `app`, password `app`

Test generated pagination/sorting endpoint:

```bash
curl "http://localhost:8080/api/products?page=0&size=10&sort=name,asc"
```

More prompt examples:

- `Create an API for Order with totalPrice (decimal), createdAt (timestamp), belongs to Customer`
- `Create an API for BlogPost with title, content, status (enum: DRAFT, PUBLISHED), authorEmail (valid email)`
- `Create an API for Invoice with amount (decimal, min 0), dueDate (date), paid (boolean)`

Try full example prompts:

```bash
./gradlew run --args="generate --file examples/prompts/ecommerce.txt --pretty"
./gradlew run --args="generate --file examples/prompts/blog.txt --pretty"
```

### Prompt -> Output Showcase

Prompt:

```text
Create an API for Product with:
- name (string, required)
- price (decimal, required, min 0)
- createdAt (timestamp)
- belongs to Category

Create an API for Category with:
- name (string, required)
```

Generated project (excerpt):

```text
generated-api/
  src/main/java/com/example/generated/
    entity/Product.java
    entity/Category.java
    dto/ProductDTO.java
    repository/ProductRepository.java
    service/ProductService.java
    controller/ProductController.java
```

Sample generated entity snippet:

```java
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal price;
    private LocalDateTime createdAt;
}
```

## CLI commands

```bash
./gradlew run --args="generate-zip --file examples/prompts/ecommerce.txt --out scaffold.zip"
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
- `--generate-zip`
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
- `features.lombokModels`: generate DTO/entity classes with Lombok annotations (`@Getter`, `@Setter`, etc.)

## Template packs

Pack descriptors:
- `templates/packs/spring-boot-3-standard.yml`
- `templates/packs/microservices-pattern.yml`
- `templates/packs/ddd-layered.yml`

Runtime templates:
- `src/main/resources/templates/spring-boot-3-standard/`

## Roadmap

- CLI: `generate --zip` convenience option to write scaffold.zip directly
- Templates: expand microservices + DDD starter packs
- Docs: add more real-world prompt examples and troubleshooting
- Quality: add more integration tests around generated projects

### Community ideas

- Add clear prompt -> generated output showcase in docs (in progress)
- Keep improving typed field hints in prompts (`BigDecimal`, `LocalDateTime`, etc.)
- Consider optional `application.properties` generation alongside `application.yml`

## Contributing

See `CONTRIBUTING.md` for setup, workflow, and PR guidance.
