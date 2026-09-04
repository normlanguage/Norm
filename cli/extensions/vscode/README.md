# Norm Language Support for VS Code

<p align="center"><img src="images/norm-256.png" alt="Norm Logo" width="128"></p>

This extension provides syntax highlighting, compiler diagnostics, type-aware completion, signature help, automatic imports, hover, definition navigation, references, rename, and execution through `norm run`. Extension functions, reflection, annotation protocols, and data-format libraries use the same compiler-backed language services as the rest of Norm.

Platform-specific release packages contain the matching self-contained Norm CLI and Java runtime. No Java installation or separate server configuration is required.

Use the play button in a Norm editor, run `Norm: Run Current File`, or press `Ctrl+F5` to save and run the active source file in a dedicated VS Code task terminal. Norm settings are available through `Norm: Open Settings`.

## Development

1. Build the CLI distribution from the repository root:

   ```powershell
   .\gradlew.bat :compiler:installRuntimeDist
   ```

2. Open the Norm repository as the VS Code workspace. A development Extension Host automatically discovers:

   ```text
   <repository>\cli\compiler\build\install\norm\bin\norm.bat
   ```

   For another layout, set `norm.cli.path` explicitly. A Norm source workspace may use a newer patch of the same major/minor line; configured, bundled, and `PATH` CLIs must match the extension exactly. The status bar shows the selected version and source. Language Server diagnostics and `Norm: Run Current File` share the same verified CLI selection.

3. Install dependencies and compile the extension:

   ```powershell
   npm install
   npm run compile
   ```

4. Open this directory in VS Code and press F5. `npm run package:local` rebuilds the current JVM CLI distribution and creates `norm-language-support-<version>-local.vsix` containing that server for direct installation.

The universal release package contains `bin/` artifacts produced for every target in `cli/compiler/release-targets.json`:

```powershell
npm run package -- <version> <binaries-directory> <output.vsix>
```

Run `npm run test:extension` for the real Extension Host suite; it builds a current CLI distribution before starting VS Code. Run `npm run smoke:lsp` for the stdio protocol handshake.

The extension is only a VS Code adapter. Language analysis remains in the Java compiler and is exposed through the editor-neutral Language Server Protocol.
