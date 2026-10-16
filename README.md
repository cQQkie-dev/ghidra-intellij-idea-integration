# Ghidra IntelliJ IDEA Integration

A Ghidra extension companion for the
[`ghidra-intellij`](https://github.com/cQQkie-dev/ghidra-intellij) IntelliJ IDEA plug-in.

It adds this Script Manager context-menu action:

```text
Edit with IntelliJ IDEA
```

The action opens the selected local Ghidra script through the IntelliJ plug-in's loopback-only
Script Editor listener.

## Requirements

- Ghidra 12.1.4 PUBLIC or a compatible Ghidra release.
- IntelliJ IDEA with the Ghidra IntelliJ plug-in installed.
- In IntelliJ:

  ```text
  Settings → Tools → Ghidra Script Editor
  → Enable Script Editor listener
  ```

The default listener port is:

```text
12321
```

## Installation

Build the extension:

```bash
./gradlew distributeExtension
```

Install the generated ZIP from:

```text
dist/
```

through Ghidra's Extension Manager, then restart Ghidra.

## Usage

1. In Ghidra CodeBrowser, open:

   ```text
   Window → Script Manager
   ```

2. Select a local script.

3. Open it through either integration action:

   ```text
   Script Manager toolbar → IntelliJ IDEA icon
   ```

   or:

   ```text
   Right-click script → Edit with IntelliJ IDEA
   ```

## Configuration

In Ghidra:

```text
Edit → Tool Options → IntelliJ IDEA Integration
```

The Script Editor Port must match the IntelliJ setting.

## Development

Create ignored local configuration:

```properties
GHIDRA_INSTALL_DIR=/absolute/path/to/ghidra
```

in:

```text
gradle.properties
```

Then build:

```bash
./gradlew classes
./gradlew distributeExtension
```