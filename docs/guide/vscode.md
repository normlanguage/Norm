# Developing with VS Code

Norm's official VS Code extension includes the current version's self-contained `norm` CLI. Installing one VSIX provides syntax highlighting, compiler diagnostics, formatting, completion, navigation, and run commands without a separate Java installation.

## Installation

1. Open the [latest Norm release](https://github.com/normlanguage/Norm/releases/latest).
2. Download the single `norm-language-support-vMAJOR.MINOR.PATCH.vsix` file.
3. Run **Extensions: Install from VSIX...** in the VS Code command palette.
4. Select the downloaded file and reload the window.

The universal VSIX contains same-version CLIs for Windows x64, Linux x64, and macOS ARM64. The extension selects the executable for the current platform.

To also run `norm` in a Windows terminal, download `norm.exe` from the same release. It runs directly; running `norm.exe setup` once installs it for the current user and configures `PATH`. Open a new terminal to use `norm`. Linux and macOS use their platform-specific CLI archives. Do not mix extensions and CLIs from different releases.

## Your first file

Create `hello.norm`:

```norm
main() {
    String language = "Norm"
    printLine("Hello, " + language)
}
```

After saving, you can:

- Click the run button at the top right of the editor.
- Run **Norm: Run Current File** from the command palette.
- Press `Ctrl+F5`, or `Cmd+F5` on macOS.

The extension first saves open Norm files in the current project, then starts the program. Output appears in a dedicated task terminal, while compiler errors appear in both the editor and Problems panel.

## Editor capabilities

### Writing code

- Highlighting for `.norm` files, keywords, types, nullable types, generics, annotations, and extension functions.
- Bracket, quote, and indentation editing behavior.
- Compiler formatting on save.
- Suggestions within incomplete returns, variable initializers, and call arguments.
- Locals, functions, types, enum variants, and templates ranked by expected type and scope.

### Understanding types and calls

- Parameter hints show named arguments, overloads, and substituted generic signatures.
- Nullable receivers offer safe-access completion.
- Container members display their actual type arguments.
- Explicitly exported extension functions support member-style completion and automatic imports.
- `Class<T>`, `Field<Owner, Value>`, `Function<Signature>`, annotation lifecycles, and JSON/XML/YAML APIs provide completion, hover, and navigation.

### Navigating projects

- Definition navigation, references, and semantic rename across files and packages.
- Compiler visibility rules for module exports, file-private declarations, and private class members.
- Precise import edits when completing public declarations from other packages.
- Diagnostics, navigation, and completion updated from unsaved in-memory documents.

Compiler semantic snapshots in `norm lsp` compute these capabilities. The extension does not duplicate type checking or name resolution.

When editing `norm/stdlib/std` in a Norm source checkout, the Language Server analyzes disk files as source overlays for the same builtin standard-library declaration identities. It does not load another user module named `std`.

## Single files and projects

A `.norm` file can declare `module()`, dependencies, and an application entry together. Local single-file applications need no `package`, Module name, or version. The CLI and Language Server read this single declaration and resolve dependencies from the specified repositories. Multi-file projects continue to use a formal package structure and a separate root `module.norm`.

See the [module system](/spec/module-system) for module rules.

## CLI selection

Released extensions use their bundled self-contained CLI by default. Set `norm.cli.path` only when developing the Norm toolchain. A configured CLI, bundled CLI, or system CLI must match the extension version. Norm source workspaces may use a workspace patch version within the same major/minor that is no older than the extension. Mismatched candidates are skipped with a reason in the Norm Language Server output. The status bar continuously shows the selected CLI version; click it to view the source and path.

Released extensions resolve the CLI in this order:

1. A matching `norm.cli.path`.
2. A development build in the current Norm source workspace with the same major/minor and a newer patch version.
3. The same-version self-contained CLI bundled with the extension.
4. A development build in the current Norm source workspace with the same patch version as the extension.
5. A same-version `norm` on the system `PATH`.

When F5 starts an extension development host, the workspace build takes priority over bundled content. The Language Server and run commands reuse the same selection rather than starting different toolchain versions.

After setting a path, run **Norm: Restart Language Server** to replace the old process.

## Troubleshooting

### Highlighting works, but diagnostics or completion do not

TextMate highlighting does not need the language server; the other features require the CLI. Run **Norm: Restart Language Server** first. If the problem persists, inspect the Norm logs in the Output panel and check that `norm.cli.path` points to an executable for the current platform.

### The terminal works, but the extension uses an old version

Check the Norm version in the status bar and click it to display the CLI path and source. The **Norm Language Server** output records the same information. If `norm.cli.path` points to a different version, the extension uses its matching bundled version and displays a notice once.

### Cross-file imports or completion are missing

Confirm that dependencies are declared in the same file's `module()` or the project's root `module.norm`, and that the target declaration is public. Single-file scripts do not automatically form a project merely by sharing a directory.

### The run command uses the wrong directory

`norm.run.workingDirectory` selects the workspace directory or current file directory. Project applications normally use the workspace directory; standalone scripts that depend on adjacent resources can use the file directory.

## Extension development

Source development packages are needed only when changing the Norm compiler or VS Code extension. See [`cli/extensions/vscode/README.md`](https://github.com/normlanguage/Norm/blob/main/cli/extensions/vscode/README.md) for the complete build, test, and local VSIX workflow. The [release process](/design/release-process) defines platforms, assets, and acceptance requirements.

The [version index](/versions/) defines the exact language-service boundary. A debugger is not yet part of the release.

Next: [Language philosophy](/guide/philosophy).
