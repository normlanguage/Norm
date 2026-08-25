package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.CoreIdentityVersion;

public final class LanguageProfile {
  private static final LanguageProfile CURRENT =
      new LanguageProfile(CoreIdentityVersion.CURRENT, new StandardLibraryPrelude());
  private final CoreIdentityVersion identityVersion;
  private final StandardLibraryPrelude standardLibrary;

  private LanguageProfile(
      CoreIdentityVersion identityVersion, StandardLibraryPrelude standardLibrary) {
    this.identityVersion = java.util.Objects.requireNonNull(identityVersion, "identityVersion");
    this.standardLibrary = java.util.Objects.requireNonNull(standardLibrary, "standardLibrary");
  }

  public static LanguageProfile current() {
    return CURRENT;
  }

  public CoreIdentityVersion identityVersion() {
    return identityVersion;
  }

  StandardLibraryPrelude standardLibrary() {
    return standardLibrary;
  }
}
