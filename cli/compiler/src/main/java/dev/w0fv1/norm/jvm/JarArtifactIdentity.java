package dev.w0fv1.norm.jvm;

public sealed interface JarArtifactIdentity
    permits JdkModuleIdentity, LocalJarIdentity, MavenJarIdentity {
  String canonical();
}
