# REST API Spec Generator (JSON)

Generate consistent API specifications and starter scaffolding from plain English prompts. The tool is meant to be an immutable utility (think `start.spring.io + AI + your standards`): others don’t edit it, they run it to get JSON specs and zipped source that you can drop into your projects.

## Highlights

- **Multi-entity prompts** – separate entity descriptions by blank lines (or another delimiter) and the generator returns each `EntityDefinition` it finds.
- **Smart suggestions** – when the prompt lacks an entity name, the generator suggests names inferred from the request or project name.
- **Two delivery modes** – CLI output for quick specs plus an HTTP service that returns JSON and ZIP artifacts.

## CLI usage

1. `./gradlew installDist`
2. `./build/install/rest-api-generator/bin/rest-api-generator --user-request "Create an API for Product with name, price" --pretty`

Alternatives:

- `./gradlew -q run --args="--user-request \"Create an API for Book with title, authorName\" --pretty"`
- `cat prompt.txt | ./build/install/rest-api-generator/bin/rest-api-generator`

### Input modes

- `--user-request "<text>"`: pass the natural-language request directly.  
- `--input <path>`: file that contains the request or a prompt with a `USER REQUEST` section.  
- stdin: pipe the prompt into the CLI.

### Output

- The CLI prints a single JSON `ApiSpecification` containing `projectName`, `basePackage`, an array of `entities` (each with field metadata), and a `suggestions` array if names had to be inferred.  
- Use that JSON as a payload for downstream tools or save it for the HTTP workflow.

## HTTP service

Start the server:  
`./gradlew run --args="--serve --port 8080"` (custom `--port` supported).

### POST /generator/spec

- Content-Type: `application/json`.  
- Body example (single or multi-entity):  
  ```
  {"prompt":"Create an API for Product with name, price\n\nCreate an API for Employee with firstName, lastName"}
  ```
- Response: the same JSON spec the CLI produces, plus optional `suggestions` when no entity name was found.

Save the response:

```
curl -X POST http://localhost:8080/generator/spec \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Create an API for Product with name, price\n\nCreate an API for Employee with firstName, lastName"}' \
  -o spec.json
```

### POST /generator/code

- Content-Type: `application/json`.  
- Body: the `spec.json` file returned earlier.  
- Response: `application/zip` with README + scaffolding for every entity (`entity`, `dto`, `repository`, `controller`).

Download and unzip:

```
curl -X POST http://localhost:8080/generator/code \
  -H "Content-Type: application/json" \
  --data-binary @spec.json \
  -o scaffold.zip
```

Files land under `src/main/java/<basePackage>/<entity|dto|repository|controller>` so you can drop them into your project, open them in an IDE, and add business logic instantly.

## Testing

- `./gradlew clean test` (includes parser, generator, code gen, and ZIP verification).
