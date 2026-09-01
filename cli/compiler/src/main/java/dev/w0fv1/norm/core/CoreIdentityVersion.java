package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreIdentityVersion(CoreSchemaVersion schema, LanguageSemanticsVersion semantics) {
  public static final CoreIdentityVersion CURRENT =
      new CoreIdentityVersion(CoreSchemaVersion.V12, LanguageSemanticsVersion.V12);

  public CoreIdentityVersion {
    Objects.requireNonNull(schema, "schema");
    Objects.requireNonNull(semantics, "semantics");
  }

  public String storageNamespace() {
    return "core-v" + schema.code() + "-semantics-v" + semantics.code();
  }
}
