package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

record SemanticAnalysisInput(
    List<Syntax.Program> programs,
    Syntax.Program entryProgram,
    boolean requireEntryPoint,
    Set<DocumentId> exportedSources,
    Map<SourceSpan, SemanticContribution> reusableDeclarations,
    int minimumBodySymbolId,
    Set<DocumentId> moduleEvaluationDocuments,
    CompilationScope scope,
    DeclarationCatalog declarations) {
  SemanticAnalysisInput {
    programs = List.copyOf(programs);
    Objects.requireNonNull(entryProgram, "entryProgram");
    exportedSources = Set.copyOf(exportedSources);
    reusableDeclarations = Map.copyOf(reusableDeclarations);
    moduleEvaluationDocuments = Set.copyOf(moduleEvaluationDocuments);
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(declarations, "declarations");
  }
}
