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
  private final TypeResolver typeResolver;
  private final BodyAnalyzer bodies;
  private final DeclarationAnalyzer declarations;
  private final DeclarationAnalysis declarationAnalysis;

  Analyzer(SemanticAnalysisInput input, DiagnosticBag diagnostics, CompilationGuard guard) {
    context = new SemanticAnalysisContext(input, diagnostics, guard);
    typeResolver =
        new TypeResolver(
            context.declarations,
            context.builtins,
            context.resolution,
            context.model,
            context.diagnostics);
    var policies =
        new DeclarationPolicyResolver(
            context.declarations, context.resolution, typeResolver, diagnostics);
    declarations =
        new DeclarationAnalyzer(
            context.builtins,
            context.declarations,
            diagnostics,
            context.model,
            context.programs,
            context.resolution,
            typeResolver,
            policies);
    bodies =
        new BodyAnalyzer(
            context.body,
            context.model,
            context.resolution,
            diagnostics,
            guard,
            context.builtins,
            typeResolver,
            declarations,
            policies,
            context.transactions);
    context.guard.checkpoint();
    declarations.collectDeclarations();
    context.model.reserveIds(context.minimumBodySymbolId);
    ImportResolver.Result imports =
        new ImportResolver()
            .resolve(
                new ImportResolver.Input(
                    context.programs,
                    context.scope,
                    context.declarations,
                    context.model.symbols(),
                    context.model.declarationSymbols()));
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
    visibility.scopes().forEach(context.body.scopes()::addSemanticScope);
    declarations.validateClassHierarchy();
    declarationAnalysis =
        new DeclarationAnalysis(
            context.model.symbols(),
            DeclarationContract.capture(context.model, typeResolver),
            typeResolver.callableGroups(),
            typeResolver.interfaceParentTypes(),
            visibility.importableSymbols(),
            diagnostics.snapshot());
  }

  DeclarationAnalysis declarations() {
    return declarationAnalysis;
  }

  FrontendAnalysis analyze(
      boolean resolveProgram,
      dev.w0fv1.norm.value.CompilationRequest.Kind kind,
      java.util.Map<SourceSpan, SemanticContribution> reusableDeclarations) {
    try (var entryScope = context.resolution.enterProgram(context.entryProgram)) {
      Syntax.FunctionDecl main =
          kind == dev.w0fv1.norm.value.CompilationRequest.Kind.LIBRARY
              ? null
              : context.entryProgram.functions().stream()
                  .filter(function -> function.name().equals("main"))
                  .findFirst()
                  .orElse(null);
      if (main == null && context.requireEntryPoint) {
        context.diagnostics.error(
            MISSING_MAIN, "program must declare 'main()'", context.syntax.span());
      } else if (main != null
          && (!typeResolver
                  .functionReturnType(main, typeResolver.functionTypeParameters(main))
                  .equals(SemanticType.VOID)
              || !main.typeParameters().isEmpty()
              || !main.parameters().isEmpty())) {
        context.diagnostics.error(TYPE_MISMATCH, "entry function must be 'main()'", main.span());
      }

      for (Syntax.Program program : context.programs) {
        context.guard.checkpoint();
        try (var programScope = context.resolution.enterProgram(program)) {

          for (Syntax.EnumDecl enumDecl : program.enums()) {
            if (reuse(enumDecl.span(), reusableDeclarations)) continue;
            typeResolver.validateTypeParameterNames(enumDecl.typeParameters());
            declarations.validateEnum(enumDecl, bodies);
          }
          for (Syntax.InterfaceDecl interfaceDecl : program.interfaces()) {
            if (reuse(interfaceDecl.span(), reusableDeclarations)) continue;
            typeResolver.validateTypeParameterNames(interfaceDecl.typeParameters());
            declarations.validateInterface(interfaceDecl, bodies);
          }
          for (Syntax.FunctionDecl function : program.functions()) {
            if (reuse(function.span(), reusableDeclarations)) continue;
            bodies.analyzeFunction(function, null);
          }
          for (Syntax.AggregateDecl aggregateDecl : program.aggregates()) {
            if (reuse(aggregateDecl.span(), reusableDeclarations)) continue;
            typeResolver.validateTypeParameterNames(aggregateDecl.typeParameters());
            declarations.validateFields(aggregateDecl, bodies.expressionChecker);
            bodies.analyzeImplicitSuperCall(aggregateDecl);
            for (Syntax.ConstructorDecl constructor : aggregateDecl.constructors()) {
              bodies.analyzeConstructor(constructor, aggregateDecl);
            }
            for (Syntax.FunctionDecl method : aggregateDecl.methods()) {
              bodies.analyzeFunction(method, aggregateDecl);
            }
          }
        }
      }
      AnnotationChecker annotationChecker =
          new AnnotationChecker(
              context.declarations,
              context.diagnostics,
              context.model,
              context.programs,
              context.resolution,
              context.scope,
              typeResolver,
              bodies.expressionChecker);
      annotationChecker.validateAnnotationSchemas();
      annotationChecker.validateAnnotationApplications();
      declarations.validateInterfaceGraphAndConformances();
      List<Diagnostic> snapshot = context.diagnostics.snapshot();
      SemanticModel semanticModel =
          context.model.build(
              context.syntax,
              context.scope,
              context.builtins,
              declarationAnalysis.callableGroups(),
              declarationAnalysis.interfaceParents(),
              context.body.scopes().semanticScopes(),
              snapshot,
              declarationAnalysis.importableSymbols());
      Optional<BoundProgram> boundProgram =
          !resolveProgram
                  || snapshot.stream()
                      .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR)
              ? Optional.empty()
              : Optional.of(new Binder(context.programs, semanticModel).bind(main));
      return new FrontendAnalysis(
          new AnalysisResult(semanticModel, Optional.ofNullable(main), snapshot), boundProgram);
    }
  }

  private boolean reuse(
      SourceSpan root, java.util.Map<SourceSpan, SemanticContribution> reusableDeclarations) {
    SemanticContribution contribution = reusableDeclarations.get(root);
    if (contribution == null) return false;
    context.model.reuse(contribution);
    contribution.scopes().forEach(context.body.scopes()::addSemanticScope);
    return true;
  }
}
