# Distribution Dependency Preflight

[简体中文](distribution-preflight.zh-CN.md)

[`distribution-preflight.mjs`](./distribution-preflight.mjs) reads the runtime dependency graph from an existing `toolchain-artifacts.json` and gathers evidence about installed dependency candidates in the current Debian or Fedora system root. It does not declare dependency versions, compile the product, install software, or access remote repositories. Node built-in modules are its only execution dependencies.

## Input and scope

The input is a build-generated schema 2 toolchain catalog. Its generator remains the sole source for the dependency graph. This tool verifies the catalog's roots, edges, physical component ownership, purpose, and storage identity, retaining logical components represented by merged JARs. Do not substitute test fixtures or hand-maintained coordinate lists for the actual investigation input.

The report records the input SHA-256, but that alone does not prove the catalog matches the current source. An investigation must also bind the source commit, actual build arguments, input supply mechanism, and generation log. This tool leaves `catalogSourceBinding` as `not-verified`.

Only the runtime graph supplied by the catalog is covered. The report does not claim to cover annotation processors, tests, plugins and their dependencies, all parent POMs or BOMs, the JDK, Native Image tools, or additional metadata archives. Missing coverage domains are marked `not-assessed`. Do not use this report directly as a complete `Build-Depends` or `BuildRequires` list.

## Collection

Run in the Debian system root under investigation:

```sh
mkdir -p evidence
node cli/compiler/scripts/distribution-preflight.mjs collect \
  --target debian \
  --catalog /path/to/generated/toolchain-artifacts.json \
  --output evidence/debian-runtime.json
node cli/compiler/scripts/distribution-preflight.mjs render \
  --input evidence/debian-runtime.json \
  --output evidence/debian-runtime.md
```

For Fedora, use the same entry points with `--target fedora` and separate output files. Provide Node and the target system's package-query tools before running it. The tool neither installs them nor starts a container for the investigation.

The report records the actual `/etc/os-release`, architecture, package list, package-query results, and input digest. `--target` chooses a distribution family; it does not assert Debian sid or Fedora Rawhide. Verify the target suite and repository snapshot separately. Do not collect on a derivative such as Ubuntu and label it Debian.

The Debian branch checks candidate POMs and JARs in `/usr/share/maven-repo`, including version aliases. It records file SHA-256, real paths, package ownership of files and symbolic links, binary package versions, and source-package identity. A directory or package version differing from upstream does not automatically prove API incompatibility; a matching version does not prove byte or behavioral equivalence.

The Fedora branch queries Maven capabilities in the local RPM database rather than guessing RPM names from artifact names. It enumerates versioned capabilities of installed packages and distinguishes ordinary JARs, POMs, and classifiers. It reports `mapping-required` if only non-default coordinates exist; even when a default package exists, it retains possible compatibility packages. Similar versions are not automatically mapped or judged API-compatible. Provider, artifact version, and source RPM are recorded. The current evidence layer is RPM capability: the corresponding JAR/POM files, bytes, module descriptors, and complete artifact mapping remain unverified.

## Result semantics

| Result | Meaning |
| --- | --- |
| `installed-candidate` | Installed system-dependency candidate evidence exists according to this branch; compatibility is untested |
| `mapping-required` | A versioned Maven capability exists, but coordinate mapping or compatibility has not been verified |
| `not-installed` | No candidate was found in this root; this does not mean the distribution repository lacks one |
| `incomplete-candidate` | Some files were found, but not enough to form the required POM/JAR candidate |
| `unverified-candidate` | Files exist, but system-package ownership of the file or Maven path cannot be established |
| `probe-error` | Query, read, environment, or evidence-format failure; do not interpret as a missing package |

`collect` returns 0 if every item has installed-candidate evidence; 2 if some items need mapping, are not installed, are incomplete, or have unconfirmed ownership; and 1 for an input or collection error. The result file retains per-item errors, though a failed precondition may prevent a report. Successful `render` returns 0.

No exit code proves a source build passed. The report's `readiness` remains `not-established`. JPMS, API compatibility, external-network isolation, source rebuilding, installation, and system-library upgrade acceptance remain unfinished. A file digest records bytes observed at collection time; it is not equivalent to distribution signature verification or package-integrity attestation.

Output files are created exclusively and do not overwrite existing evidence. Collection does not modify system repositories or user caches. If the package list changes during collection, the tool rejects a successful report. Report times come from the execution environment's clock; record clock skew when archiving.

## Tests

```sh
node --test cli/compiler/scripts/distribution-preflight.test.mjs
```

Focused tests use temporary directories and system-command fixtures to cover error classification, component ownership, symbolic links, version aliases, report scope, and real Node subprocesses. They do not replace collection from real Norm catalogs on Debian sid and Fedora Rawhide, or `sbuild`, `mock`, and post-install acceptance.
