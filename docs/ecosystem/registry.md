# Norm Package Registry

The registry publishes only Module artifacts. Java Bindings and platform implementations are optional implementation metadata inside an artifact; they do not create another module kind or change how consumers declare dependencies.

See the [package manager](/ecosystem/package-manager) for Module repository coordinates and [Java Library Adapter](/design/java-library-adapters) for the interoperability boundary with Java dependencies and Maven/Gradle repositories. [`normlanguage/registry`](https://github.com/normlanguage/registry) is the single source of truth for `github` repository identity mapping: an entry contains only the Module name, GitHub owner, and repository. Each Module repository owns its sources, version tags, and immutable NAR Releases; the Norm compiler repository does not store third-party or official Java Binding packages.
