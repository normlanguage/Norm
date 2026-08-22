package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class CompilationEnvironment {
  private static final DiagnosticCode INVALID_MODULE = new DiagnosticCode("NORM-MODULE-0001");
  private static final CompilationEnvironment STANDARD =
      new CompilationEnvironment(() -> {}, () -> {});
  private final ConcurrentHashMap<ParseKey, ParsedDocument> parsedDocuments =
      new ConcurrentHashMap<>();
  private final Runnable parseObserver;
  private final Runnable analysisObserver;
  private final StandardLibraryPrelude standardLibrary;

  private CompilationEnvironment(Runnable parseObserver, Runnable analysisObserver) {
    this.parseObserver = Objects.requireNonNull(parseObserver, "parseObserver");
    this.analysisObserver = Objects.requireNonNull(analysisObserver, "analysisObserver");
    standardLibrary = new StandardLibraryPrelude(this);
  }

  public static CompilationEnvironment standard() {
    return STANDARD;
  }

  public static CompilationEnvironment create(Runnable parseObserver) {
    return new CompilationEnvironment(parseObserver, () -> {});
  }

  public static CompilationEnvironment create(Runnable parseObserver, Runnable analysisObserver) {
    return new CompilationEnvironment(parseObserver, analysisObserver);
  }

  StandardLibraryPrelude standardLibrary() {
    return standardLibrary;
  }

  ParsedDocument parse(SourceFile source, boolean manifest) {
    ParseKey key = new ParseKey(source.id(), manifest);
    return parsedDocuments.compute(
        key,
        (ignored, existing) ->
            existing != null && existing.source().text().equals(source.text())
                ? existing
                : parseUncached(source, manifest));
  }

  void analysisStarted() {
    analysisObserver.run();
  }

  private ParsedDocument parseUncached(SourceFile source, boolean manifest) {
    parseObserver.run();
    DiagnosticBag diagnostics = new DiagnosticBag();
    if (manifest) {
      try {
        new ModuleManifestParser().parse(source);
      } catch (IllegalArgumentException exception) {
        diagnostics.error(
            INVALID_MODULE, exception.getMessage(), new SourceSpan(source, 0, source.length()));
      }
      Syntax.Program syntax =
          new Syntax.Program(
              "",
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              new SourceSpan(source, 0, source.length()));
      return new ParsedDocument(source, List.of(), syntax, diagnostics.snapshot());
    }
    List<Token> tokens = new Lexer(source, diagnostics).lex();
    Syntax.Program syntax = new Parser(source, tokens, diagnostics).parse();
    return new ParsedDocument(source, tokens, syntax, diagnostics.snapshot());
  }

  private record ParseKey(DocumentId document, boolean manifest) {}
}
