package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.CoreIdentityVersion;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;

public final class LanguageProfile {
  private static final LanguageProfile KERNEL =
      new LanguageProfile(
          CoreIdentityVersion.CURRENT,
          CompilationPrelude.empty(),
          java.util.Set.of(),
          java.util.Set.of());
  private final CoreIdentityVersion identityVersion;
  private final CompilationPrelude prelude;
  private final java.util.Set<DocumentId> moduleEvaluationDocuments;
  private final java.util.Set<DocumentId> standardLibraryDocuments;

  private LanguageProfile(
      CoreIdentityVersion identityVersion,
      CompilationPrelude prelude,
      java.util.Set<DocumentId> moduleEvaluationDocuments,
      java.util.Set<DocumentId> standardLibraryDocuments) {
    this.identityVersion = java.util.Objects.requireNonNull(identityVersion, "identityVersion");
    this.prelude = java.util.Objects.requireNonNull(prelude, "prelude");
    this.moduleEvaluationDocuments = java.util.Set.copyOf(moduleEvaluationDocuments);
    this.standardLibraryDocuments = java.util.Set.copyOf(standardLibraryDocuments);
  }

  public static LanguageProfile kernel() {
    return KERNEL;
  }

  public static LanguageProfile withPrelude(CompilationPrelude prelude) {
    return new LanguageProfile(
        CoreIdentityVersion.CURRENT, prelude, java.util.Set.of(), prelude.documentIds());
  }

  public LanguageProfile moduleEvaluation(DocumentId entryDocument) {
    return new LanguageProfile(
        identityVersion, prelude, java.util.Set.of(entryDocument), standardLibraryDocuments);
  }

  public CoreIdentityVersion identityVersion() {
    return identityVersion;
  }

  CompilationPrelude prelude() {
    return prelude;
  }

  public java.util.Optional<SourceFile> preludeSource(DocumentId document) {
    return prelude.source(document);
  }

  java.util.Set<DocumentId> moduleEvaluationDocuments() {
    return moduleEvaluationDocuments;
  }

  java.util.Set<DocumentId> standardLibraryDocuments() {
    return standardLibraryDocuments;
  }
}
