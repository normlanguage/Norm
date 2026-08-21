# Norm Language Support for VS Code

This extension provides syntax highlighting, compiler diagnostics, completion, hover, definition navigation, references, rename, and execution through `norm run`.

Platform-specific release packages contain the matching standalone Norm CLI. No Java installation or separate server configuration is required.

Use the play button in a Norm editor, run `Norm: Run Current File`, or press `Ctrl+F5` to save and run the active source file in a dedicated VS Code task terminal. Norm settings are available through `Norm: Open Settings`.

## Development

1. Build the CLI distribution from the repository root:

   ```powershell
   .\gradlew.bat :cli:installDist
   ```

2. Open the Norm repository as the VS Code workspace. The extension automatically discovers:

   ```text
   <repository>\tool\cli\app\build\install\norm\bin\norm.bat
   ```

   For another layout, set `norm.cli.path` explicitly. CLI resolution uses the configured path, `NORM_CLI`, the bundled release CLI, repository development builds, then `PATH`.

3. Install dependencies and compile the extension:

   ```powershell
   npm install
   npm run compile
   ```

4. Open this directory in VS Code and press F5, or run `npm run package` and install the resulting VSIX.

Run `npm run test:extension` for the real Extension Host suite; it builds a current CLI distribution before starting VS Code. Run `npm run smoke:lsp` for the stdio protocol handshake.

The extension is only a VS Code adapter. Language analysis remains in the Java compiler and is exposed through the editor-neutral Language Server Protocol.
