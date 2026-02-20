# Phase 3 Implementation

Phase 3 introduces plugin extensibility for custom generators.

## Delivered

- Plugin SDK metadata annotation:
  - `src/main/java/io/restapigen/core/plugin/PluginDefinition.java`
- Dynamic plugin discovery and loading:
  - `src/main/java/io/restapigen/core/plugin/PluginLoader.java`
  - Built-ins are always registered first.
  - Classpath plugins are loaded via `ServiceLoader`.
  - External JAR directories are scanned from `plugins.externalDirectories`.
  - Explicit plugin classes are instantiated from `plugins.externalClassNames`.
- Generation now resolves plugins at runtime from config:
  - `src/main/java/io/restapigen/codegen/CodeGenerator.java`
- CLI support for plugin visibility:
  - `plugins list` command in `src/main/java/io/restapigen/cli/Main.java`

## Config additions

`plugins` section now supports:
- `externalDirectories`: directories containing plugin JARs
- `externalClassNames`: fully-qualified class names to load reflectively

## How to use custom plugins

1. Build a plugin JAR implementing `GeneratorPlugin`.
2. Place JAR into `plugins/` (or configure another directory).
3. Add plugin name to `plugins.enabled`.
4. If needed, set class in `plugins.externalClassNames`.
5. Run `plugins list` to verify discovery.

## Tests

- Loader tests:
  - `src/test/java/io/restapigen/core/plugin/PluginLoaderTest.java`
- End-to-end ZIP generation with external plugin class:
  - `src/test/java/io/restapigen/codegen/CodeGeneratorTest.java`
