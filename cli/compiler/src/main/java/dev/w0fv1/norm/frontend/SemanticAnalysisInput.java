package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationScope;
import java.util.List;
import java.util.Objects;
import java.util.Set;

record SemanticAnalysisInput(
    List<Syntax.Program> programs,
    Syntax.Program entryProgram,
    boolean requireEntryPoint,
    Set<DocumentId> exportedSources,
    int minimumBodySymbolId,
    Set<DocumentId> moduleEvaluationDocuments,
    Set<DocumentId> standardLibraryDocuments,
    Set<DocumentId> bindingDocuments,
    CompilationScope scope,
    DeclarationCatalog declarations) {
  SemanticAnalysisInput {
    programs = List.copyOf(programs);
    Objects.requireNonNull(entryProgram, "entryProgram");
    exportedSources = Set.copyOf(exportedSources);
    moduleEvaluationDocuments = Set.copyOf(moduleEvaluationDocuments);
    standardLibraryDocuments = Set.copyOf(standardLibraryDocuments);
    bindingDocuments = Set.copyOf(bindingDocuments);
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(declarations, "declarations");
  }
}
