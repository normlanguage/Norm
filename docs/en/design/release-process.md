# Release process

Norm releases are triggered by semantic Git tags. The SemVer value in the tag is the sole release-version source for the CLI, language server, VS Code extension, asset names, and GitHub Release. Published versions are never reused.

## Assets

Every release ships standalone CLIs for each platform and one universal VS Code extension containing every supported CLI:

| Platform | CLI |
| --- | --- |
| Windows x64 | `norm.exe` in ZIP |
| Linux x64 | `norm` in TAR.GZ |
| macOS Apple Silicon | `norm` in TAR.GZ |

`norm-language-support-vMAJOR.MINOR.PATCH.vsix` is the only extension asset. It selects its bundled CLI from the host operating system and architecture. Norm does not publish platform-specific VSIX packages.

GraalVM 25 no longer provides new macOS Intel builds, so Norm does not establish a release line tied to the retired macOS x64 toolchain. A new platform must first pass the same acceptance suite in continuous integration.

## Release gates

A release must pass the Java toolchain tests, VS Code static checks, native CLI version verification, Hello World, every executable acceptance program under `norm/tests`, and a native LSP handshake. The universal VSIX must contain every CLI in the target manifest and verifies each one byte-for-byte against its accepted platform binary.

The workflow generates SHA-256 checksums and build provenance after every platform succeeds. Assets enter a draft release first and become public together; a failed platform prevents the entire release.

## Automation

The [release-target manifest](https://github.com/w0fv1/norm/blob/main/tool/cli/release-targets.json) is the sole machine definition for platforms, runners, CLI paths, and extension directories; the packager and [Release workflow](https://github.com/w0fv1/norm/blob/main/.github/workflows/release.yml) both consume it. Regular CI detects Native Image regressions early. The release workflow accepts only `vMAJOR.MINOR.PATCH` tags.

Public releases should progressively adopt Windows Authenticode signing and Apple Developer ID signing with notarization. Until signing is available, release notes must state that the operating system may display an origin warning.

## Release notes

Release notes record only delivered language behavior, tooling changes, migration requirements, and known limitations. A release requires Chinese and English version records at the `major.minor` path derived from its tag. The latest implementation contract in the [version index](/en/versions/) defines the current boundary; future language specifications are not current compiler commitments.
