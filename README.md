# REST API Generator

**Generate production-ready Spring Boot REST APIs from plain English in 30 seconds.**

No boilerplate. No copy-pasting. Type a description → get a complete, runnable ZIP.

[![CI](https://github.com/rrezartprebreza/rest-api-generator/actions/workflows/ci.yml/badge.svg)](https://github.com/rrezartprebreza/rest-api-generator/actions)

---

## ⚡ Quickest start (Docker)

```bash
docker run -p 8080:8080 ghcr.io/rrezartprebreza/rest-api-generator:latest
```

Open **http://localhost:8080** → write a prompt → click **Download ZIP** → run your API.

Published image: `ghcr.io/rrezartprebreza/rest-api-generator`

Tags:
- `latest` from `main`
- branch tags from CI, for example `main` or `develop`
- commit SHA tags from CI
- release tags from `v*`, for example `1.0.0`, `1.0`, `1`

---

## What gets generated

| Layer | File | Details |
|---|---|---|
| Entity | `Product.java` | JPA, auditing (`createdAt`/`updatedAt`), relationships |
| DTO | `ProductDTO.java` | Bean Validation (`@NotNull`, `@Email`, `@Min`) |
| Repository | `ProductRepository.java` | JpaRepository + JpaSpecificationExecutor |
| Service | `ProductService.java` | `@Transactional`, pagination, filter |
| Controller | `ProductController.java` | REST endpoints, `@Valid`, proper HTTP status codes |
| Mapper | `ProductMapper.java` | MapStruct interface |
| Error handling | `GlobalExceptionHandler.java` | 404, 400 field errors, 500 — all JSON |
| Tests | `ProductServiceTest.java` | Mockito unit tests with real assertions |
| Integration test | `ProductIntegrationTest.java` | MockMvc against H2 |
| Migration | `V1__create_products_table.sql` | Flyway/Liquibase |
| Docker | `Dockerfile` + `docker-compose.yml` | Multi-stage build + DB + Adminer |
| Config | `application.yml` | Env var placeholders (`${DB_URL:...}`) |

**Time saved: ~2-3 hours → 30 seconds per entity.**

---

## Build from source

**Requirements:** Java 17+, Gradle (or use `./gradlew`)

```bash
git clone https://github.com/rrezartprebreza/rest-api-generator
cd rest-api-generator
./gradlew installDist
```

### Web UI (recommended)

```bash
./gradlew run --args="serve --port 8080"
# Open http://localhost:8080
```

### CLI

```bash
# Generate JSON spec
./gradlew run --args="generate --prompt 'Create an API for Product with name, price' --pretty"

# Generate ZIP directly
./gradlew run --args="generate-zip --file examples/prompts/ecommerce.txt --out scaffold.zip"

# Unzip and run
unzip scaffold.zip -d my-api && cd my-api && ./gradlew bootRun
```

---

## Prompt syntax

```
Create an API for Product with:
- name (string, required)
- price (decimal, required, min 0)
- status (enum: DRAFT, ACTIVE, ARCHIVED)
- createdAt (timestamp)
- belongs to Category

Create an API for Category with:
- name (string, required)
- description (string)
```

**Field type hints:** `string`, `integer`, `decimal`, `boolean`, `date`, `timestamp`, `email`

**Relationships:** `belongs to X` → `@ManyToOne`, `has many X` → `@OneToMany`, `many-to-many with X` → `@ManyToMany`

**Constraints:** `required`, `min 0`, `max 255`, `valid email`, `unique`, `nullable`

---

## HTTP API

Start the server: `./gradlew run --args="serve --port 8080"`

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Web UI |
| `GET` | `/about` | Server info |
| `GET` | `/health` | Health check |
| `POST` | `/generator/spec` | Prompt → JSON spec |
| `POST` | `/generator/code` | JSON spec → ZIP scaffold |

```bash
# Step 1: parse prompt → spec
curl -X POST http://localhost:8080/generator/spec \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Create an API for Product with name, price"}' \
  -o spec.json

# Step 2: spec → ZIP
curl -X POST http://localhost:8080/generator/code \
  -H "Content-Type: application/json" \
  --data-binary @spec.json -o scaffold.zip
```

---

## Self-host with Docker

```bash
# Run the published image
docker run -p 8080:8080 ghcr.io/rrezartprebreza/rest-api-generator:latest

# Or build and run locally from this repo
docker build -t rest-api-generator:local .
docker run -p 8080:8080 rest-api-generator:local

# Or use docker compose for local source changes
docker compose up

# Rebuild after changes
docker compose up --build
```

The Web UI is served at **http://localhost:8080** — share this URL with your team.

---

## Configuration (`.rest-api-generator.yml`)

```bash
# Generate a default config
./gradlew run --args="init"
```

```yaml
project:
  basePackage: com.mycompany.api
  springBootVersion: "3.2.1"
  javaVersion: "17"
  templatePack: spring-boot-3-standard

standards:
  database:
    type: postgresql          # postgresql | mysql | h2
    migrationTool: flyway     # flyway | liquibase | none
  layering:
    includeServiceLayer: true

features:
  auditing: true              # adds createdAt / updatedAt automatically
  dockerArtifacts: true
  lombokModels: false
```

---

## CLI commands

```bash
./gradlew run --args="generate --prompt '...' --pretty"   # JSON spec
./gradlew run --args="generate-zip --file prompt.txt"     # ZIP scaffold
./gradlew run --args="serve --port 8080"                  # Web UI + HTTP API
./gradlew run --args="openapi --prompt '...'"             # OpenAPI YAML
./gradlew run --args="init"                               # Write default config
./gradlew run --args="validate"                           # Validate config
./gradlew run --args="templates list"                     # List template packs
./gradlew run --args="plugins list"                       # List plugins
```

Every command supports `--help`.

---

## Template packs

| Pack | Description |
|---|---|
| `spring-boot-3-standard` | Standard layered architecture (default) |
| `microservices-pattern` | Microservice-ready structure |
| `ddd-layered` | Domain-Driven Design layers |

---

## Tests

```bash
./gradlew clean test
docker build -t rest-api-generator:local .
```

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). PRs welcome.

---

## License

[MIT](LICENSE)
