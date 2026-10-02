package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JdkModuleIdentity(String name) implements JarArtifactIdentity {
  public JdkModuleIdentity {
    Objects.requireNonNull(name, "name");
    if (!name.matches("[A-Za-z][A-Za-z0-9_.]*")) {
      throw new IllegalArgumentException("invalid JDK module name: " + name);
    }
  }

  @Override
  public String canonical() {
    return "jdk:" + name;
  }
}
