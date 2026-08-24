package dev.w0fv1.norm.core;

import java.util.Objects;

public final class DefinitionHasher {
  private static final String DOMAIN_SEPARATOR = "norm:core:group:v1\0";

  private DefinitionHasher() {}

  public static DefinitionGroupId hashGroup(byte[] canonicalGroup) {
    Objects.requireNonNull(canonicalGroup, "canonicalGroup");
    return new DefinitionGroupId(
        ContentHasher.hash(DOMAIN_SEPARATOR, CoreIdentityVersion.CURRENT, canonicalGroup));
  }
}
