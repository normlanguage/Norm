package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.builtin.BuiltinSymbols;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SemanticAnalysisContext {

  final Syntax.Program syntax;
  final List<Syntax.Program> programs;
  final Syntax.Program entryProgram;
  final DiagnosticBag diagnostics;
  final boolean requireEntryPoint;
  final Set<DocumentId> exportedSources;
  final CompilationScope scope;
  final CompilationGuard guard;
  final Map<SourceSpan, SemanticContribution> reusableDeclarations;
  final int minimumBodySymbolId;
  final DeclarationCatalog declarations;

  final BuiltinSymbols builtins;

  final SemanticModelBuilder model;
  final BodyAnalysisState body = new BodyAnalysisState();
  final TypeResolutionState resolution = new TypeResolutionState();

  SemanticAnalysisContext(
      SemanticAnalysisInput input, DiagnosticBag diagnostics, CompilationGuard guard) {
    this.programs = input.programs();
    this.entryProgram = input.entryProgram();
    this.syntax = merge(programs, entryProgram);
    this.diagnostics = diagnostics;
    this.requireEntryPoint = input.requireEntryPoint();
    this.exportedSources = input.exportedSources();
    this.scope = input.scope();
    this.guard = java.util.Objects.requireNonNull(guard, "guard");
    this.declarations = input.declarations();
    this.reusableDeclarations = input.reusableDeclarations();
    this.minimumBodySymbolId = input.minimumBodySymbolId();
    this.builtins =
        new BuiltinSymbols(
            input.moduleEvaluationDocuments(),
            input.standardLibraryDocuments(),
            input.bindingDocuments());
    model = new SemanticModelBuilder(builtins);
  }

  static Syntax.Program merge(List<Syntax.Program> programs, Syntax.Program entryProgram) {
    List<Syntax.EnumDecl> enums = new ArrayList<>();
    List<Syntax.InterfaceDecl> interfaces = new ArrayList<>();
    List<Syntax.AggregateDecl> aggregates = new ArrayList<>();
    List<Syntax.FunctionDecl> functions = new ArrayList<>();
    for (Syntax.Program program : programs) {
      enums.addAll(program.enums());
      interfaces.addAll(program.interfaces());
      aggregates.addAll(program.aggregates());
      functions.addAll(program.functions());
    }
    return new Syntax.Program(
        entryProgram.packageName(),
        entryProgram.packageAnnotations(),
        entryProgram.imports(),
        enums,
        interfaces,
        aggregates,
        functions,
        entryProgram.span());
  }

  record FunctionReferenceResolution(
      Syntax.FunctionDecl declaration,
      List<SemanticType> reifiedArguments,
      SemanticType functionType) {
    FunctionReferenceResolution {
      reifiedArguments = List.copyOf(reifiedArguments);
      Objects.requireNonNull(functionType, "functionType");
    }
  }

  record InterfaceRequirement(
      Syntax.InterfaceDecl owner,
      SemanticType receiver,
      Syntax.InterfaceMethodDecl method,
      List<ParameterInfo> parameters,
      SemanticType result,
      String key,
      String signature) {
    InterfaceRequirement {
      parameters = List.copyOf(parameters);
    }
  }

  record TypeProbe(SemanticType type, boolean hasErrors) {}

  record AnalysisCheckpoint(
      SemanticModelBuilder.Checkpoint model,
      BodyAnalysisState.Checkpoint body,
      TypeResolutionState.Checkpoint resolution,
      int diagnosticMark) {}
}
