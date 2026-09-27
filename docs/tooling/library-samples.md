---
title: Library samples
description: Run library-owned Norm examples with matching packages
---

# Library samples

Each library's `samples/` index points to its executable examples or to the owning library's examples. Start at the English or Chinese index and follow the owning example's run or test command. Its `Module module()` declaration is the source of truth for package names and versions. [Library repositories](https://github.com/orgs/normlanguage/repositories) provide the indexes; the [Norm `hello` examples](/tooling/) show complete programs without a separate library checkout.

Use a Norm CLI release and library NAR versions built for the same compiler ABI. Once those versions are published, the owning example's command resolves its declared dependencies normally. A matching package must exist for every dependency; a source checkout alone does not install it. Published release assets and their SHA-256 sidecars are attached to each library's GitHub release.

## Preparing unpublished source

Before a new library release, keep candidate packages in an isolated Norm home. Use the library's `module.norm` and the standard `norm package` command, in dependency order. If a changed package's current version is already published, declare a new version; never replace an existing published asset or mix packages built against different versions of their dependencies. Update the consuming sample's dependency versions to match.

The Norm CLI starts Java, including when launched through `norm.exe`. Set that Java process's `user.home` to an empty development directory and package into that home's `.norm/cache/packages`. For example, from a Unix shell in a workspace containing `commons-lang`:

```sh
export JAVA_TOOL_OPTIONS="-Duser.home=$PWD/.norm-sample-home"
norm package commons-lang/commons/lang/module.norm --output "$PWD/.norm-sample-home/.norm/cache/packages"
```

In PowerShell, set `$env:JAVA_TOOL_OPTIONS = "-Duser.home=$PWD/.norm-sample-home"` and use the same output directory. Keep that setting for the subsequent sample run so the CLI resolves the packages from the same home.

The package command checks the module and writes a NAR plus checksum. For dependencies that themselves name other libraries, package their declared versions first. [Module source files](https://github.com/orgs/normlanguage/repositories) and the [release process](/design/release-process) identify the exact source and publishable asset for each version. A local candidate is a development build; a public sample is ready for general installation only after its entire dependency graph is released and its declared versions are updated.
