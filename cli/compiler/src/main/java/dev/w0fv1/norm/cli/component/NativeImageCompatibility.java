package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.jvm.JarBindingClasspath;
import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.project.ApplicationCompilation;
import java.util.List;

final class NativeImageCompatibility {
  private NativeImageCompatibility() {}

  static List<String> arguments(ApplicationCompilation compilation) {
    boolean hibernate =
        JarBindingClasspath.artifacts(compilation.sourceSet().jarBindings()).stream()
            .map(artifact -> artifact.identity())
            .filter(MavenJarIdentity.class::isInstance)
            .map(MavenJarIdentity.class::cast)
            .map(identity -> identity.coordinate())
            .anyMatch(
                coordinate ->
                    coordinate.group().equals("org.hibernate.orm")
                        && coordinate.artifact().equals("hibernate-core"));
    if (!hibernate) return List.of();
    return List.of(
        "-H:ServiceLoaderFeatureExcludeServiceProviders="
            + "org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl",
        "-H:ExcludeResources=META-INF/services/org[.]hibernate[.]bytecode[.]spi[.]BytecodeProvider");
  }
}
