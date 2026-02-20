# Publish Checklist

1. Verify local tests pass:
   - `./gradlew clean test`
2. Verify CLI smoke flows:
   - `./gradlew -q run --args="templates list"`
   - `./gradlew -q run --args="plugins list --config .rest-api-generator.yml"`
3. Verify HTTP flow:
   - start server: `./gradlew run --args="serve --port 8080"`
   - generate spec with `POST /generator/spec`
   - generate zip with `POST /generator/code`
4. Confirm docs are updated:
   - `README.md`
   - `docs/PHASE1-IMPLEMENTATION.md`
   - `docs/PHASE2-IMPLEMENTATION.md`
   - `docs/PHASE3-IMPLEMENTATION.md`
   - `docs/PHASE4-IMPLEMENTATION.md`
5. Confirm schemas match runtime:
   - `schemas/rest-api-generator-config.schema.json`
   - `schemas/api-specification.schema.json`
6. Commit and push to `main`.
7. Confirm GitHub Actions CI passes.
