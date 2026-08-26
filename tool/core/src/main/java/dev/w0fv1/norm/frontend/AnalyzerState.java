package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.semantic.BuiltinSymbols;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIndex;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeRelations;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.LexicalLifetime;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AnalyzerState {

  static final DiagnosticCode DUPLICATE_NAME = new DiagnosticCode("NORM-NAME-0001");
  static final DiagnosticCode MISSING_MAIN = new DiagnosticCode("NORM-NAME-0002");
  static final DiagnosticCode UNKNOWN_NAME = new DiagnosticCode("NORM-NAME-0003");
  static final DiagnosticCode TYPE_MISMATCH = new DiagnosticCode("NORM-TYPE-0001");
  static final DiagnosticCode INVALID_CALL = new DiagnosticCode("NORM-TYPE-0002");
  static final DiagnosticCode INVALID_CONTROL = new DiagnosticCode("NORM-FLOW-0001");
  static final DiagnosticCode NULLABILITY_MISMATCH = new DiagnosticCode("NORM-NULL-0001");
  static final DiagnosticCode UNTYPED_NULL = new DiagnosticCode("NORM-NULL-0002");
  static final DiagnosticCode INVALID_NULLABLE_TYPE = new DiagnosticCode("NORM-NULL-0003");
  static final DiagnosticCode UNSAFE_NULLABLE_ACCESS = new DiagnosticCode("NORM-NULL-0004");
  static final SemanticType STRINGABLE =
      SemanticType.declared(
          "std.core.Stringable", "Stringable", List.of(), ValueCategory.POLYMORPHIC);

  final Syntax.Program syntax;
  final List<Syntax.Program> programs;
  final Syntax.Program entryProgram;
  final DiagnosticBag diagnostics;
  final OverloadResolver overloads;
  final TypeRelations.DeclarationGraph typeRelations;
  final boolean requireEntryPoint;
  final Set<DocumentId> exportedSources;
  final CompilationScope scope;
  final CompilationGuard guard;
  final Map<SourceSpan, SemanticContribution> reusableDeclarations;
  final int minimumBodySymbolId;
  final DeclarationCatalog declarations;
  final Map<String, SemanticType> typeParameterBounds = new HashMap<>();
  final BuiltinSymbols builtins;
  final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
  final Map<SourceSpan, SymbolId> bindings = new LinkedHashMap<>();
  final Map<SourceSpan, SemanticType> semanticTypes = new LinkedHashMap<>();
  final Map<SourceSpan, ResolvedCall> resolvedCalls = new LinkedHashMap<>();
  final Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments = new LinkedHashMap<>();
  final Map<SourceSpan, ResolvedIteration> iterations = new LinkedHashMap<>();
  final Map<SourceSpan, ResolvedIndex> indexes = new LinkedHashMap<>();
  final Map<SourceSpan, LexicalLifetime> referenceLifetimes = new LinkedHashMap<>();
  final Map<SymbolId, List<SymbolId>> members = new LinkedHashMap<>();
  final Map<String, SymbolId> typeSymbols = new LinkedHashMap<>();
  final Map<Object, SymbolId> declarationSymbols = new IdentityHashMap<>();
  final Map<String, SymbolId> copyMethods = new HashMap<>();
  final Map<Syntax.ImportDecl, SymbolId> importAliases = new IdentityHashMap<>();
  final Map<SymbolId, List<SymbolId>> aliasTargets = new LinkedHashMap<>();
  final Map<SymbolId, Map<SymbolId, SymbolId>> witnesses = new LinkedHashMap<>();
  final Map<String, SemanticType> aggregateParents = new LinkedHashMap<>();
  final Map<SymbolId, SymbolId> methodOverrides = new LinkedHashMap<>();
  final FlowScopes flowScopes = new FlowScopes();
  int nextSymbolId;
  SymbolId currentCallable;
  SemanticType expectedReturnType = SemanticType.VOID;
  boolean implicitSelfReturn;
  Map<String, SemanticType> activeTypeParameters = Map.of();
  Map<String, SymbolId> activeTypeParameterSymbols = Map.of();
  Syntax.Program currentProgram;
  Syntax.AggregateDecl currentAggregate;
  final Deque<ControlContext> controls = new ArrayDeque<>();
  final Set<SymbolId> assignedLocals = new HashSet<>();
  final Set<SymbolId> capturedLocals = new HashSet<>();
  final Set<SymbolId> reportedMutableCaptures = new HashSet<>();
  final Deque<Set<SymbolId>> lambdaLocals = new ArrayDeque<>();

  AnalyzerState(
      List<Syntax.Program> programs,
      Syntax.Program entryProgram,
      DiagnosticBag diagnostics,
      boolean requireEntryPoint,
      Set<DocumentId> exportedSources,
      CompilationGuard guard,
      Map<SourceSpan, SemanticContribution> reusableDeclarations,
      int minimumBodySymbolId,
      Set<DocumentId> moduleEvaluationDocuments,
      CompilationScope scope) {
    this.programs = List.copyOf(programs);
    this.entryProgram = entryProgram;
    this.syntax = merge(programs, entryProgram);
    this.diagnostics = diagnostics;
    this.typeRelations = new TypeRelations.DeclarationGraph(this::isNominallyAssignable);
    this.overloads = new OverloadResolver(diagnostics, INVALID_CALL, typeRelations, this::typeOf);
    this.requireEntryPoint = requireEntryPoint;
    this.exportedSources = Set.copyOf(exportedSources);
    this.scope = java.util.Objects.requireNonNull(scope, "scope");
    this.guard = java.util.Objects.requireNonNull(guard, "guard");
    this.declarations = new DeclarationCatalog(this.programs, this.exportedSources, scope);
    this.reusableDeclarations = Map.copyOf(reusableDeclarations);
    this.minimumBodySymbolId = minimumBodySymbolId;
    this.builtins = new BuiltinSymbols(moduleEvaluationDocuments);
    symbols.putAll(builtins.symbols());
    symbols.values().stream()
        .filter(symbol -> symbol.kind() == SymbolKind.TYPE)
        .forEach(symbol -> typeSymbols.put(symbol.type().identity(), symbol.id()));
    builtins.members().forEach((owner, values) -> members.put(owner, new ArrayList<>(values)));
  }

  abstract SemanticType typeOf(Syntax.Expression expression, SemanticType expected);

  abstract boolean isNominallyAssignable(SemanticType expected, SemanticType actual);

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
        entryProgram.imports(),
        enums,
        interfaces,
        aggregates,
        functions,
        entryProgram.span());
  }

  enum ControlKind {
    LOOP,
    SWITCH
  }

  static final class ControlContext {
    private final ControlKind kind;
    private SemanticType resultType;
    private LexicalLifetime referenceLifetime;

    private ControlContext(ControlKind kind, SemanticType resultType) {
      this.kind = kind;
      this.resultType = resultType;
    }

    static ControlContext loop() {
      return new ControlContext(ControlKind.LOOP, null);
    }

    static ControlContext switchExpression(SemanticType resultType) {
      return new ControlContext(ControlKind.SWITCH, resultType);
    }

    ControlKind kind() {
      return kind;
    }

    SemanticType resultType() {
      return resultType;
    }

    void setResultType(SemanticType resultType) {
      this.resultType = resultType;
    }

    LexicalLifetime referenceLifetime() {
      return referenceLifetime;
    }

    void mergeReferenceLifetime(LexicalLifetime lifetime) {
      referenceLifetime =
          referenceLifetime == null ? lifetime : referenceLifetime.narrowest(lifetime);
    }
  }

  record SourceCallResolution(
      Syntax.FunctionDecl declaration,
      List<ParameterInfo> parameters,
      List<SemanticType> reifiedArguments,
      SemanticType result) {
    SourceCallResolution {
      parameters = List.copyOf(parameters);
      reifiedArguments = List.copyOf(reifiedArguments);
    }
  }

  record FunctionReferenceResolution(
      Syntax.FunctionDecl declaration, List<SemanticType> reifiedArguments) {
    FunctionReferenceResolution {
      reifiedArguments = List.copyOf(reifiedArguments);
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

  record InterfaceCallResolution(
      List<ParameterInfo> parameters, SemanticType result, List<SemanticType> reifiedArguments) {
    InterfaceCallResolution {
      parameters = List.copyOf(parameters);
      reifiedArguments = List.copyOf(reifiedArguments);
    }
  }

  record SourceCallCandidate(
      SourceCallResolution resolution,
      List<String> missingTypeArguments,
      List<InferenceConflict> conflicts,
      List<BoundViolation> boundViolations,
      boolean assignable,
      int score) {
    SourceCallCandidate {
      missingTypeArguments = List.copyOf(missingTypeArguments);
      conflicts = List.copyOf(conflicts);
      boundViolations = List.copyOf(boundViolations);
    }

    boolean applicable() {
      return missingTypeArguments.isEmpty()
          && conflicts.isEmpty()
          && boundViolations.isEmpty()
          && assignable;
    }

    List<ParameterInfo> parameters() {
      return resolution.parameters();
    }
  }

  record InferenceConflict(String name, SemanticType first, SemanticType second) {}

  record BoundViolation(String name, SemanticType bound, SemanticType actual) {}

  record TypeProbe(SemanticType type, boolean hasErrors) {}

  record AnalysisCheckpoint(
      Map<SourceSpan, SymbolId> bindings,
      Map<SourceSpan, SemanticType> semanticTypes,
      Map<SourceSpan, ResolvedCall> resolvedCalls,
      Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments,
      Map<SourceSpan, ResolvedIteration> iterations,
      Map<SourceSpan, ResolvedIndex> indexes,
      Map<SourceSpan, LexicalLifetime> referenceLifetimes,
      FlowScopes.FlowState flowState,
      int semanticScopeCount,
      int diagnosticMark) {}
}
