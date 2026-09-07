package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticSeverity;
import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.semantic.AnalysisResult;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.List;
import java.util.Optional;

final class Analyzer {
  private final SemanticAnalysisContext context;
  private final TypeSystem typeSystem;
  private final BodyAnalyzer bodies;
  private final DeclarationAnalyzer declarations;

  Analyzer(SemanticAnalysisInput input, DiagnosticBag diagnostics, CompilationGuard guard) {
    context = new SemanticAnalysisContext(input, diagnostics, guard);
    typeSystem =
        new TypeSystem(
            context.declarations,
            context.builtins,
            context.resolution,
            context.model,
            context.diagnostics);
    bodies = new BodyAnalyzer(context, typeSystem);
    declarations = new DeclarationAnalyzer(context, typeSystem, bodies);
  }

  FrontendAnalysis analyze(boolean resolveProgram) {
    context.guard.checkpoint();
    declarations.collectDeclarations();
    context.model.reserveIds(context.minimumBodySymbolId);
    ImportResolver.Result imports =
        new ImportResolver()
            .resolve(
                new ImportResolver.Input(
                    context.programs,
                    context.declarations,
                    context.model.symbols(),
                    context.model.declarationSymbols(),
                    context.model.nextSymbolId()));
    imports.diagnostics().forEach(context.diagnostics::report);
    context.model.imports(imports);
    VisibilityResolver.Result visibility =
        new VisibilityResolver(
                new VisibilityResolver.Input(
                    context.programs,
                    context.scope,
                    context.model.symbols(),
                    context.model.declarationSymbols(),
                    context.model.importAliases(),
                    context.declarations,
                    context.exportedSources))
            .build();
    visibility.scopes().forEach(context.body.flowScopes::addSemanticScope);
    declarations.validateClassHierarchy();
    context.resolution.currentProgram = context.entryProgram;
    Syntax.FunctionDecl main =
        context.entryProgram.functions().stream()
            .filter(function -> function.name().equals("main"))
            .findFirst()
            .orElse(null);
    if (main == null && context.requireEntryPoint) {
      context.diagnostics.error(
          MISSING_MAIN, "program must declare 'main()'", context.syntax.span());
    } else if (main != null
        && (!typeSystem
                .functionReturnType(main, typeSystem.functionTypeParameters(main))
                .equals(SemanticType.VOID)
            || !main.typeParameters().isEmpty()
            || !main.parameters().isEmpty())) {
      context.diagnostics.error(TYPE_MISMATCH, "entry function must be 'main()'", main.span());
    }

    for (Syntax.Program program : context.programs) {
      context.guard.checkpoint();
      context.resolution.currentProgram = program;
      for (Syntax.EnumDecl enumDecl : program.enums()) {
        if (reuse(enumDecl.span())) continue;
        typeSystem.validateTypeParameterNames(enumDecl.typeParameters());
        declarations.validateEnum(enumDecl);
      }
      for (Syntax.InterfaceDecl interfaceDecl : program.interfaces()) {
        if (reuse(interfaceDecl.span())) continue;
        typeSystem.validateTypeParameterNames(interfaceDecl.typeParameters());
        declarations.validateInterface(interfaceDecl);
      }
      for (Syntax.FunctionDecl function : program.functions()) {
        if (reuse(function.span())) continue;
        bodies.analyzeFunction(function, null);
      }
      for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
        if (reuse(aggregateDecl.span())) continue;
        typeSystem.validateTypeParameterNames(aggregateDecl.typeParameters());
        declarations.validateFields(aggregateDecl);
        for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
          bodies.analyzeConstructor(constructor, aggregateDecl);
        }
        for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
          bodies.analyzeFunction(method, aggregateDecl);
        }
      }
    }
    AnnotationChecker annotationChecker =
        new AnnotationChecker(context, typeSystem, bodies.expressionChecker);
    annotationChecker.validateAnnotationSchemas();
    annotationChecker.validateAnnotationApplications();
    declarations.validateInterfaceGraphAndConformances();
    List<Diagnostic> snapshot = context.diagnostics.snapshot();
    SemanticModel semanticModel =
        context.model.build(
            context.syntax,
            context.scope,
            context.builtins,
            typeSystem.callableGroups(),
            typeSystem.interfaceParentTypes(),
            context.body.flowScopes.semanticScopes(),
            snapshot,
            visibility.importableSymbols());
    Optional<BoundProgram> boundProgram =
        !resolveProgram
                || snapshot.stream()
                    .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
            ? Optional.empty()
            : Optional.of(new Binder(context.programs, semanticModel).bind(main));
    return new FrontendAnalysis(
        new AnalysisResult(semanticModel, Optional.ofNullable(main), snapshot), boundProgram);
  }

  private boolean reuse(SourceSpan root) {
    SemanticContribution contribution = context.reusableDeclarations.get(root);
    if (contribution == null) return false;
    context.model.reuse(contribution);
    contribution.scopes().forEach(context.body.flowScopes::addSemanticScope);
    return true;
  }
}
