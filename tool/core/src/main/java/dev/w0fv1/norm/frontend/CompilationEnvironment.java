package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.CoreIdentityVersion;
import dev.w0fv1.norm.core.store.DefinitionStore;
import dev.w0fv1.norm.core.store.FileDefinitionStore;
import dev.w0fv1.norm.core.store.InMemoryDefinitionStore;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class CompilationEnvironment {
  private static final DiagnosticCode INVALID_MODULE = new DiagnosticCode("NORM-MODULE-0001");
  private final ConcurrentHashMap<ParseKey, ParsedDocument> parsedDocuments =
      new ConcurrentHashMap<>();
  private final Runnable parseObserver;
  private final Runnable analysisObserver;
  private final StandardLibraryPrelude standardLibrary;
  private final DefinitionStore definitionStore;

  private CompilationEnvironment(Runnable parseObserver, Runnable analysisObserver) {
    this(parseObserver, analysisObserver, new InMemoryDefinitionStore());
  }

  private CompilationEnvironment(
      Runnable parseObserver, Runnable analysisObserver, DefinitionStore definitionStore) {
    this.parseObserver = Objects.requireNonNull(parseObserver, "parseObserver");
    this.analysisObserver = Objects.requireNonNull(analysisObserver, "analysisObserver");
    this.definitionStore = Objects.requireNonNull(definitionStore, "definitionStore");
    standardLibrary = StandardLibraryPrelude.shared(this);
  }

  public static CompilationEnvironment standard() {
    return new CompilationEnvironment(() -> {}, () -> {});
  }

  public static CompilationEnvironment persistent() throws IOException {
    Path root =
        Path.of(
            System.getProperty("user.home"),
            ".norm",
            "cache",
            "definitions",
            CoreIdentityVersion.CURRENT.storageNamespace());
    return persistent(root);
  }

  public static CompilationEnvironment persistent(Path root) throws IOException {
    return new CompilationEnvironment(() -> {}, () -> {}, new FileDefinitionStore(root));
  }

  public static CompilationEnvironment create(Runnable parseObserver) {
    return new CompilationEnvironment(parseObserver, () -> {});
  }

  public static CompilationEnvironment create(Runnable parseObserver, Runnable analysisObserver) {
    return new CompilationEnvironment(parseObserver, analysisObserver);
  }

  public static CompilationEnvironment create(
      Runnable parseObserver, Runnable analysisObserver, DefinitionStore definitionStore) {
    return new CompilationEnvironment(parseObserver, analysisObserver, definitionStore);
  }

  StandardLibraryPrelude standardLibrary() {
    return standardLibrary;
  }

  DefinitionStore definitionStore() {
    return definitionStore;
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
