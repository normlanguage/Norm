# Release process

Norm releases are triggered by semantic Git tags. The SemVer value in the tag is the sole release-version source for the CLI, language server, VS Code extension, asset names, and GitHub Release. Published versions are never reused.

## Assets

Every release ships a self-contained CLI for each platform and one universal VS Code extension containing every supported CLI:

| Platform | CLI |
| --- | --- |
| Windows x64 | Directly executable and self-installing `norm.exe` |
| Linux x64 | `norm/bin/norm` in TAR.GZ |
| macOS Apple Silicon | `norm/bin/norm` in TAR.GZ |

Every platform uses the same runtime made from `bin`, compiler `lib`, and the JDK 25 `jlink` `runtime`. The Windows `norm.exe` embeds that directory unchanged and atomically expands it by version on first use. `norm.exe setup` installs the executable for the current user, updates the user `PATH` idempotently, and prepares the pinned GraalVM Community Native Image toolchain. The native toolchain is not duplicated inside the CLI and universal VSIX; Norm downloads the platform archive into `~/.norm/toolchains/native-image`, verifies the official SHA-256, and installs it atomically. The first native build uses the same process when setup was skipped.

`norm-language-support-vMAJOR.MINOR.PATCH.vsix` is the only extension asset. It selects a bundled directory with the same structure from the host operating system and architecture. Norm does not publish platform-specific VSIX packages.

A new platform must first pass the same acceptance suite in continuous integration.

## Release gates

All platform CLIs and the universal VSIX are built before final acceptance. The toolchain suite covers language programs once through `ProgramExecutionTest`. Each platform verifies source execution, Java interoperability, one native build with three isolated executions, LSP and editor integration. Windows also checks portable execution and idempotent setup. VSIX validation checks all embedded runtimes and executes the host bundle.

Framework and application acceptance belongs to adapter repositories and [examples](https://github.com/normlanguage/examples), not the compiler release.

The workflow generates SHA-256 checksums and build provenance after every platform succeeds. Assets enter a draft release first and become public together; a failed platform prevents the entire release.

## Automation

The [CLI acceptance entry point](https://github.com/normlanguage/Norm/blob/main/cli/compiler/scripts/verify-cli.mjs) covers compiler delivery and generic Java interoperability.

The [release-target manifest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/release-targets.json) is the sole machine definition for platforms, runners, distribution directories, launchers, and extension directories; the packager and [Release workflow](https://github.com/normlanguage/Norm/blob/main/.github/workflows/release.yml) both consume it. Regular CI verifies the toolchain. Native size is a separate manual workflow. The release workflow accepts only `vMAJOR.MINOR.PATCH` tags.

Public releases should progressively adopt Windows Authenticode signing and Apple Developer ID signing with notarization. Until signing is available, release notes must state that the operating system may display an origin warning.

## Release notes

Release notes record only delivered language behavior, tooling changes, migration requirements, and known limitations. A release requires Chinese and English version records at the `major.minor` path derived from its tag. The latest implementation contract in the [version index](/en/versions/) defines the current boundary; future language specifications are not current compiler commitments.
