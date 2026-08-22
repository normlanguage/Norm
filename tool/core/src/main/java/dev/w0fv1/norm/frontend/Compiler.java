package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.TypedProgram;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class Compiler {
  private final CompilationEnvironment environment;

  public Compiler() {
    this(CompilationEnvironment.standard());
  }

  public Compiler(CompilationEnvironment environment) {
    this.environment = java.util.Objects.requireNonNull(environment, "environment");
  }

  public CompilationResult compile(SourceFile source) {
    return compile(CompilationRequest.single(source));
  }

  public CompilationResult compile(CompilationRequest request) {
    java.util.Objects.requireNonNull(request, "request");
    AnalysisResult analysis = snapshot(request, true, Set.of()).analysis();
    if (analysis.hasErrors() || analysis.boundProgram().isEmpty()) {
      return new CompilationResult(Optional.empty(), analysis.diagnostics());
    }
    return new CompilationResult(
        Optional.of(new TypedProgram(analysis.boundProgram().orElseThrow())),
        analysis.diagnostics());
  }

  public AnalysisResult analyze(CompilationRequest request) {
    return snapshot(request).analysis();
  }

  public CompilationSnapshot snapshot(SourceFile source) {
    java.util.Objects.requireNonNull(source, "source");
    Set<dev.w0fv1.norm.value.DocumentId> manifests =
        ModuleManifest.isManifest(source) ? Set.of(source.id()) : Set.of();
    return snapshot(CompilationRequest.single(source), false, manifests);
  }

  public CompilationSnapshot snapshot(CompilationRequest request) {
    return snapshot(request, false, Set.of());
  }

  private CompilationSnapshot snapshot(
      CompilationRequest request,
      boolean requireEntryPoint,
      Set<dev.w0fv1.norm.value.DocumentId> manifests) {
    java.util.Objects.requireNonNull(request, "request");
    DiagnosticBag diagnostics = new DiagnosticBag();
    java.util.LinkedHashMap<dev.w0fv1.norm.value.DocumentId, ParsedDocument> parsedByDocument =
        new java.util.LinkedHashMap<>();
    environment
        .standardLibrary()
        .documents()
        .forEach(parsed -> parsedByDocument.put(parsed.source().id(), parsed));
    for (SourceFile source : request.sources()) {
      boolean manifest =
          manifests.contains(source.id())
              || ModuleManifest.isManifest(source) && !declaresPackage(source);
      parsedByDocument.put(source.id(), environment.parse(source, manifest));
    }
    List<ParsedDocument> parsedDocuments = List.copyOf(parsedByDocument.values());
    parsedDocuments.forEach(parsed -> parsed.diagnostics().forEach(diagnostics::report));
    List<Syntax.Program> programs =
        parsedDocuments.stream()
            .map(ParsedDocument::syntax)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    Syntax.Program entryProgram = null;
    Set<dev.w0fv1.norm.value.DocumentId> exportedSources =
        new LinkedHashSet<>(environment.standardLibrary().exportedSources());
    exportedSources.addAll(request.exportedSources());
    for (ParsedDocument parsed : parsedDocuments) {
      if (parsed.source().id().equals(request.entryDocument())) entryProgram = parsed.syntax();
    }
    environment.analysisStarted();
    AnalysisResult analysis =
        new Analyzer(
                programs,
                java.util.Objects.requireNonNull(entryProgram),
                diagnostics,
                requireEntryPoint,
                exportedSources)
            .analyze();
    return new CompilationSnapshot(request.entryDocument(), parsedDocuments, analysis);
  }

  public AnalysisResult analyze(SourceFile source) {
    return snapshot(source).analysis();
  }

  private static boolean declaresPackage(SourceFile source) {
    var tokens = new Lexer(source, new DiagnosticBag()).lex();
    return !tokens.isEmpty() && tokens.getFirst().kind() == dev.w0fv1.norm.syntax.TokenKind.PACKAGE;
  }
}
