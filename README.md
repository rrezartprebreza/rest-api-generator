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

> `ghcr.io/rrezartprebreza/rest-api-generator` runs the **generator service** (this project), not your generated API.
> After downloading a ZIP, run that generated project from its own folder.

Published image: `ghcr.io/rrezartprebreza/rest-api-generator`

Tags:
- `latest` from `main`
- branch tags from CI, for example `main` or `develop`
- commit SHA tags from CI
- release tags from `v*`, for example `1.0.0`, `1.0`, `1`

Version flow:
- `release` branch uses a fixed version such as `1.0.0`
- `main` continues with the next development version such as `1.1.0-SNAPSHOT`
- create Git tags like `v1.0.0` from `release` for published builds

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

### Important: generator app vs generated app

- **Generator app (this repo):** serves UI + `/generator/spec` + `/generator/code`
- **Generated app (your ZIP output):** your own Spring Boot API with its own `Dockerfile`/`docker-compose.yml`
- Run generated app commands only after `unzip scaffold.zip -d my-api && cd my-api`

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

### Prompt intelligence mode

- The default parser is deterministic and local (rule-based), keeping runs fast and reproducible.
- Runtime selection is environment-based:
- `APP_ENV=local` prefers `OLLAMA_URL` first, then `LLM_API_KEY`, then deterministic.
- `APP_ENV=production` prefers `LLM_API_KEY` first, then `OLLAMA_URL`, then deterministic.
- If `APP_ENV` is unset, the fallback order is `LLM_API_KEY` → `OLLAMA_URL` → deterministic.
- For local Docker runs, `docker compose` defaults `APP_ENV=local` and points `OLLAMA_URL` at the local Ollama sidecar.
- To enable free-form prompt support locally, start with `--profile llm` and pull the model once.
- The UI badge always reflects the live state: **"LLM active"**, **"LLM offline"**, or no badge (deterministic only).

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

Four options — with or without LLM:

```bash
# A) Run the published image (deterministic parser — no LLM, no extra storage needed)
docker run -p 8080:8080 ghcr.io/rrezartprebreza/rest-api-generator:latest

# B) Run locally with Ollama for free-form prompt support (~2 GB disk for the model)
docker compose --profile llm up
docker exec rest-api-generator-ollama-1 ollama pull llama3.2

# C) Build and run locally from this repo (deterministic, no LLM)
docker build -t rest-api-generator:local .
docker run -p 8080:8080 rest-api-generator:local

# D) Run for production with Groq
docker run -p 8080:8080 \
  -e APP_ENV=production \
  -e LLM_API_KEY=your-groq-key \
  ghcr.io/rrezartprebreza/rest-api-generator:latest

# E) Use docker compose — rebuilds from source (for the generator, not your generated app)
docker compose up --build
```

The Web UI is served at **http://localhost:8080** — share this URL with your team.

For local development, use `APP_ENV=local` with Ollama. For deployed/production environments, set `APP_ENV=production` and `LLM_API_KEY` so Groq is preferred automatically.

The UI shows a status badge: **"LLM active"** when the selected provider is reachable and **"LLM offline"** when it is not. If the active provider is unavailable, generation falls back automatically.

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
