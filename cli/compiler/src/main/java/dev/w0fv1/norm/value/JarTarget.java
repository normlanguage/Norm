package dev.w0fv1.norm.value;

public sealed interface JarTarget permits JdkModuleTarget, LocalJarTarget, MavenJarTarget {}
