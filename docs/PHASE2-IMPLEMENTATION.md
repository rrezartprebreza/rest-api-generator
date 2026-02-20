# Phase 2 Implementation

This phase adds essential production features on top of the Phase 1 plugin foundation.

## Delivered

- Stronger spec validation in `src/main/java/io/restapigen/core/validator/SpecValidator.java`:
  - entity and field naming rules
  - field range validation (`min <= max`)
  - enum/type compatibility checks
  - API path format checks
  - relationship target and type validation
- Template pack selection is now real:
  - config field `project.templatePack`
  - CLI override via `--template`
  - orchestrator uses selected pack
  - fallback to `spring-boot-3-standard` when a template file is missing
- Test generation upgrades:
  - unit tests and integration tests controlled by config
  - integration test template added (`integration-test.java.tpl`)
- Migration generation upgrades:
  - relationship-aware SQL for FK and many-to-many join tables
  - Liquibase changelog generation support (`migrationTool: liquibase`)

## Validation and tests

- Added validator unit tests: `src/test/java/io/restapigen/core/validator/SpecValidatorTest.java`
- Expanded ZIP generation tests for migration and integration test artifacts:
  - `src/test/java/io/restapigen/codegen/CodeGeneratorTest.java`

## Notes

- Relationship validation now requires relationship targets to exist in the same specification.
- Template packs `microservices-pattern` and `ddd-layered` currently use fallback templates unless pack-specific templates are added.
