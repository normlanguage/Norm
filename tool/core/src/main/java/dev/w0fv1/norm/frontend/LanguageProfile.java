package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.CoreIdentityVersion;

public final class LanguageProfile {
  private static final LanguageProfile KERNEL =
      new LanguageProfile(
          CoreIdentityVersion.CURRENT, CompilationPrelude.empty(), java.util.Set.of());
  private final CoreIdentityVersion identityVersion;
  private final CompilationPrelude prelude;
  private final java.util.Set<dev.w0fv1.norm.value.DocumentId> moduleEvaluationDocuments;

  private LanguageProfile(
      CoreIdentityVersion identityVersion,
      CompilationPrelude prelude,
      java.util.Set<dev.w0fv1.norm.value.DocumentId> moduleEvaluationDocuments) {
    this.identityVersion = java.util.Objects.requireNonNull(identityVersion, "identityVersion");
    this.prelude = java.util.Objects.requireNonNull(prelude, "prelude");
    this.moduleEvaluationDocuments = java.util.Set.copyOf(moduleEvaluationDocuments);
  }

  public static LanguageProfile kernel() {
    return KERNEL;
  }

  public static LanguageProfile withPrelude(CompilationPrelude prelude) {
    return new LanguageProfile(CoreIdentityVersion.CURRENT, prelude, java.util.Set.of());
  }

  public LanguageProfile moduleEvaluation(dev.w0fv1.norm.value.DocumentId entryDocument) {
    return new LanguageProfile(identityVersion, prelude, java.util.Set.of(entryDocument));
  }

  public CoreIdentityVersion identityVersion() {
    return identityVersion;
  }

  CompilationPrelude prelude() {
    return prelude;
  }

  public java.util.Optional<dev.w0fv1.norm.value.SourceFile> preludeSource(
      dev.w0fv1.norm.value.DocumentId document) {
    return prelude.source(document);
  }

  java.util.Set<dev.w0fv1.norm.value.DocumentId> moduleEvaluationDocuments() {
    return moduleEvaluationDocuments;
  }
}
