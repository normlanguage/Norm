import dev.w0fv1.norm.packaging.MavenProviderModules

plugins {
    id("norm.compiler")
    alias(libs.plugins.extra.modules)
    alias(libs.plugins.spotless)
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get())
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

extraJavaModuleInfo {
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j", "org.eclipse.lsp4j")
    automaticModule("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc", "org.eclipse.lsp4j.jsonrpc")
    automaticModule("org.graalvm.buildtools:graalvm-reachability-metadata", "graalvm.reachability.metadata")
    automaticModule("org.graalvm.buildtools:utils", "org.graalvm.buildtools.utils")
    automaticModule("org.graalvm.truffle:truffle-dsl-processor", "org.graalvm.truffle.dsl.processor")
    automaticModule(MavenProviderModules.COMPONENTS.first().groupArtifact(), MavenProviderModules.COMPONENTS.first().moduleName()) {
        MavenProviderModules.COMPONENTS.drop(1).forEach { mergeJar(it.groupArtifact()) }
    }
}
