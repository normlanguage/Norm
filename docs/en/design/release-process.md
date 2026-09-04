# Release process

Norm releases are triggered by semantic Git tags. The SemVer value in the tag is the sole release-version source for the CLI, language server, VS Code extension, asset names, and GitHub Release. Published versions are never reused.

## Assets

Every release ships a self-contained CLI for each platform and one universal VS Code extension containing every supported CLI:

| Platform | CLI |
| --- | --- |
| Windows x64 | Directly executable and self-installing `norm.exe` |
| Linux x64 | `norm/bin/norm` in TAR.GZ |
| macOS Apple Silicon | `norm/bin/norm` in TAR.GZ |

Every platform uses the same runtime made from `bin`, compiler `lib`, and the JDK 25 `jlink` `runtime`. The Windows `norm.exe` embeds that directory unchanged and atomically expands it by version on first use. `norm.exe setup` installs the executable for the current user and adds its directory to the user `PATH` idempotently. Users do not install Java, while third-party Java bindings and annotation processors remain dynamically loadable.

`norm-language-support-vMAJOR.MINOR.PATCH.vsix` is the only extension asset. It selects a bundled directory with the same structure from the host operating system and architecture. Norm does not publish platform-specific VSIX packages.

A new platform must first pass the same acceptance suite in continuous integration.

## Release gates

A release must pass the Java toolchain tests, Windows launcher tests, VS Code static checks, CLI version verification, Hello World, every executable acceptance program under `norm/tests`, a dynamic Java-binding program, and an LSP handshake. Windows additionally verifies portable execution, setup, idempotent `PATH` registration, execution after setup, and an application EXE with NAR and Java dependencies running offline from an empty cache. The universal VSIX verifies the launcher, compiler, and runtime from every accepted platform directory, then extracts and executes the complete host bundle.

The workflow generates SHA-256 checksums and build provenance after every platform succeeds. Assets enter a draft release first and become public together; a failed platform prevents the entire release.

## Automation

The [release-target manifest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/release-targets.json) is the sole machine definition for platforms, runners, distribution directories, launchers, and extension directories; the packager and [Release workflow](https://github.com/normlanguage/Norm/blob/main/.github/workflows/release.yml) both consume it. Regular CI verifies the self-contained distribution and dynamic Java loading. The release workflow accepts only `vMAJOR.MINOR.PATCH` tags.

Public releases should progressively adopt Windows Authenticode signing and Apple Developer ID signing with notarization. Until signing is available, release notes must state that the operating system may display an origin warning.

## Release notes

Release notes record only delivered language behavior, tooling changes, migration requirements, and known limitations. A release requires Chinese and English version records at the `major.minor` path derived from its tag. The latest implementation contract in the [version index](/en/versions/) defines the current boundary; future language specifications are not current compiler commitments.
