package dev.w0fv1.norm.language;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.TestIndex;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceLocation;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.AstNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SemanticQuery {
  private static final Comparator<SourceLocation> LOCATION_ORDER =
      Comparator.comparing((SourceLocation location) -> location.document().uri().toString())
          .thenComparingInt(SourceLocation::startOffset)
          .thenComparingInt(SourceLocation::endOffset);
  private final CompilationSnapshot snapshot;
  private final LanguageService language;
  private final Map<DocumentId, DocumentRevision> revisions;
  private final Map<DocumentId, List<SourceSpan>> declarations;

  SemanticQuery(
      CompilationSnapshot snapshot, LanguageService language, java.util.Set<DocumentId> documents) {
    this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
    this.language = Objects.requireNonNull(language, "language");
    var revisions = new LinkedHashMap<DocumentId, DocumentRevision>();
    var declarations = new LinkedHashMap<DocumentId, List<SourceSpan>>();
    if (!snapshot.documentIds().containsAll(documents))
      throw new IllegalArgumentException("query documents must belong to the snapshot");
    documents.stream()
        .sorted(Comparator.comparing(id -> id.uri().toString()))
        .forEach(
            id -> {
              var document = snapshot.document(id).orElseThrow();
              revisions.put(id, DocumentRevision.of(document.source()));
              var syntax = document.syntax();
              List<AstNode> nodes = new ArrayList<>();
              nodes.addAll(syntax.functions());
              nodes.addAll(syntax.aggregates());
              nodes.addAll(syntax.interfaces());
              nodes.addAll(syntax.enums());
              syntax
                  .aggregates()
                  .forEach(
                      aggregate -> {
                        nodes.addAll(aggregate.fields());
                        nodes.addAll(aggregate.constructors());
                        nodes.addAll(aggregate.methods());
                      });
              syntax.interfaces().forEach(declaration -> nodes.addAll(declaration.methods()));
              syntax.enums().forEach(declaration -> nodes.addAll(declaration.variants()));
              declarations.put(
                  id,
                  nodes.stream()
                      .map(AstNode::span)
                      .sorted(Comparator.comparingInt(SourceSpan::length))
                      .toList());
            });
    this.revisions = Map.copyOf(revisions);
    this.declarations = Map.copyOf(declarations);
  }

  public List<DocumentRevision> documents() {
    return revisions.values().stream()
        .sorted(Comparator.comparing(revision -> revision.document().uri().toString()))
        .toList();
  }

  public List<Diagnostic> diagnostics() {
    return snapshot.diagnostics();
  }

  public QueryPage<Declaration> search(String text, int offset, int limit) {
    String search = Objects.requireNonNull(text, "text").toLowerCase(Locale.ROOT);
    List<Declaration> matches =
        snapshot.semanticModel().symbols().stream()
            .filter(
                symbol ->
                    symbol
                        .declaration()
                        .filter(location -> revisions.containsKey(location.document()))
                        .isPresent())
            .filter(
                symbol ->
                    switch (symbol.kind()) {
                      case LOCAL_VARIABLE, PARAMETER, TYPE_PARAMETER, SELF -> false;
                      default -> true;
                    })
            .filter(symbol -> symbol.name().toLowerCase(Locale.ROOT).contains(search))
            .sorted(
                Comparator.comparing(
                        (Symbol symbol) -> symbol.declaration().orElseThrow(), LOCATION_ORDER)
                    .thenComparing(symbol -> symbol.id().value()))
            .map(this::describe)
            .toList();
    return QueryPage.of(matches, offset, limit);
  }

  public Context context(
      SymbolId id, DocumentRevision expected, int offset, int limit, boolean includeSource) {
    Declaration selected = declaration(id, expected);
    SourceLocation location = selected.symbol().declaration().orElseThrow();
    Optional<SourceSpan> span =
        declarations.get(location.document()).stream()
            .filter(
                candidate ->
                    candidate.startOffset() <= location.startOffset()
                        && candidate.endOffset() >= location.endOffset())
            .findFirst();
    var model = snapshot.semanticModel();
    List<Declaration> dependencies =
        span.stream()
            .flatMap(root -> model.declarationDependencies(root).stream())
            .sorted(LOCATION_ORDER)
            .flatMap(
                target -> model.resolvedSymbolAt(target.document(), target.startOffset()).stream())
            .filter(symbol -> !symbol.id().equals(id))
            .filter(
                symbol ->
                    span.isEmpty()
                        || !symbol
                            .declaration()
                            .orElseThrow()
                            .document()
                            .equals(location.document())
                        || !span.orElseThrow()
                            .location()
                            .contains(symbol.declaration().orElseThrow().startOffset()))
            .distinct()
            .map(this::describe)
            .toList();
    List<SourceLocation> references =
        language
            .references(snapshot.analysis(location.document()), location.startOffset(), false)
            .stream()
            .sorted(LOCATION_ORDER)
            .toList();
    List<Declaration> tests =
        TestIndex.from(model).forDeclaration(id).stream()
            .flatMap(test -> model.symbol(test).stream())
            .map(this::describe)
            .sorted(
                Comparator.comparing(
                    test -> test.symbol().declaration().orElseThrow(), LOCATION_ORDER))
            .toList();
    return new Context(
        selected,
        includeSource ? span : Optional.empty(),
        QueryPage.of(dependencies, offset, limit),
        QueryPage.of(references, offset, limit),
        QueryPage.of(tests, offset, limit));
  }

  public Declaration declaration(SymbolId id, DocumentRevision expected) {
    Objects.requireNonNull(expected, "expected");
    if (!expected.equals(revisions.get(expected.document())))
      throw new StaleRevisionException(expected.document());
    Symbol symbol =
        snapshot
            .semanticModel()
            .symbol(id)
            .orElseThrow(() -> new IllegalArgumentException("unknown symbol identity"));
    if (symbol.declaration().isEmpty()
        || !symbol.declaration().orElseThrow().document().equals(expected.document()))
      throw new IllegalArgumentException("symbol does not belong to the selected document");
    return describe(symbol);
  }

  private Declaration describe(Symbol symbol) {
    return new Declaration(
        symbol,
        SymbolPresentation.signature(
            SymbolPresentation.annotation(snapshot.semanticModel(), symbol)),
        Optional.ofNullable(revisions.get(symbol.declaration().orElseThrow().document())));
  }

  public record Declaration(Symbol symbol, String signature, Optional<DocumentRevision> revision) {
    public Declaration {
      Objects.requireNonNull(symbol, "symbol");
      Objects.requireNonNull(signature, "signature");
      Objects.requireNonNull(revision, "revision");
    }
  }

  public record Context(
      Declaration declaration,
      Optional<SourceSpan> source,
      QueryPage<Declaration> dependencies,
      QueryPage<SourceLocation> references,
      QueryPage<Declaration> tests) {}

  public static final class StaleRevisionException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    StaleRevisionException(DocumentId document) {
      super("document content changed: " + document.uri());
    }
  }
}
