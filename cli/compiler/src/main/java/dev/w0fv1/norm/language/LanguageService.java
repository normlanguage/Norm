package dev.w0fv1.norm.language;

import dev.w0fv1.norm.frontend.CompilationControl;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.SourceFormatter;
import dev.w0fv1.norm.semantic.AnalysisResult;
import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceLocation;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.LanguageSyntax;
import dev.w0fv1.norm.value.CompilationRequest;
import java.util.List;
import java.util.Optional;

public final class LanguageService implements AutoCloseable {
  private final CompilerSession compiler;
  private final SourceFormatter formatter = new SourceFormatter();
  private final CompletionEngine completions = new CompletionEngine();
  private final SignatureHelpResolver signatures = new SignatureHelpResolver();

  public LanguageService() {
    this(new CompilerSession());
  }

  public LanguageService(CompilerSession compiler) {
    this.compiler = java.util.Objects.requireNonNull(compiler, "compiler");
  }

  public SemanticQuery query(CompilationSnapshot snapshot) {
    return query(snapshot, snapshot.documentIds());
  }

  public SemanticQuery query(CompilationSnapshot snapshot, java.util.Set<DocumentId> documents) {
    return new SemanticQuery(snapshot, this, documents);
  }

  public RefactorPreview previewRename(
      CompilationRequest request,
      dev.w0fv1.norm.semantic.SymbolId identity,
      DocumentRevision revision,
      String newName) {
    CompilationSnapshot before = snapshot(request);
    var query = query(before, request.scope().coordinates().keySet());
    var selected = query.declaration(identity, revision);
    var declaration = selected.symbol().declaration().orElseThrow();
    var rename =
        rename(before.analysis(declaration.document()), declaration.startOffset(), newName)
            .orElseThrow(() -> new IllegalArgumentException("declaration cannot be renamed"));
    var grouped =
        rename.locations().stream()
            .collect(java.util.stream.Collectors.groupingBy(SourceLocation::document));
    if (!request.scope().coordinates().keySet().containsAll(grouped.keySet())
        || grouped.keySet().stream().anyMatch(request.bindingSources()::contains))
      throw new IllegalArgumentException("rename includes read-only or generated sources");
    var sources = new java.util.ArrayList<SourceFile>();
    var changes = new java.util.ArrayList<RefactorPreview.DocumentChange>();
    for (SourceFile source :
        request.sources().stream()
            .sorted(java.util.Comparator.comparing(value -> value.id().uri().toString()))
            .toList()) {
      StringBuilder text = new StringBuilder(source.text());
      var edits = new java.util.ArrayList<RefactorPreview.Replacement>();
      int boundary = source.length();
      for (SourceLocation location :
          grouped.getOrDefault(source.id(), List.of()).stream()
              .distinct()
              .sorted(java.util.Comparator.comparingInt(SourceLocation::startOffset).reversed())
              .toList()) {
        if (location.endOffset() > boundary)
          throw new IllegalArgumentException("rename locations overlap");
        String oldText = source.text().substring(location.startOffset(), location.endOffset());
        if (!oldText.equals(newName)) {
          edits.add(new RefactorPreview.Replacement(location, oldText, newName));
          text.replace(location.startOffset(), location.endOffset(), newName);
        }
        boundary = location.startOffset();
      }
      SourceFile changed = SourceFile.of(source.id(), text.toString());
      sources.add(changed);
      if (!edits.isEmpty())
        changes.add(
            new RefactorPreview.DocumentChange(
                DocumentRevision.of(source), DocumentRevision.of(changed), edits));
    }
    var edited =
        new CompilationRequest(
            request.unit(),
            request.scope(),
            request.entryDocument(),
            sources,
            request.exportedSources(),
            request.bindingSources());
    var after = snapshot(edited);
    return new RefactorPreview(
        query.documents(), changes, before.diagnostics(), after.diagnostics());
  }

  public AnalysisResult analyze(SourceFile source) {
    return compiler.analyze(source);
  }

  public AnalysisResult analyze(CompilationRequest request) {
    return compiler.analyze(request);
  }

  public CompilationSnapshot snapshot(CompilationRequest request, CompilationControl control) {
    return compiler.snapshot(request, control);
  }

  public CompilationSnapshot snapshot(CompilationRequest request) {
    return compiler.snapshot(request);
  }

  @Override
  public void close() {
    compiler.close();
  }

  public Optional<String> standardLibrarySource(DocumentId document) {
    return compiler.preludeSource(document).map(SourceFile::text);
  }

  public CompilationSnapshot standardLibrarySnapshot(
      java.util.Collection<SourceFile> sources,
      DocumentId entryDocument,
      CompilationControl control) {
    return compiler.preludeSnapshot(sources, entryDocument, control);
  }

  public Optional<String> format(SourceFile source) {
    return formatter.format(source);
  }

  public List<Completion> complete(AnalysisResult analysis, int offset) {
    SourceFile source = analysis.semanticModel().source();
    if (offset < 0 || offset > source.length()) {
      throw new IllegalArgumentException("completion offset is outside the source");
    }
    return complete(document(analysis), offset);
  }

  public List<Completion> complete(DocumentSemanticModel document, int offset) {
    return completions.complete(document, offset);
  }

  public List<TestDeclaration> tests(DocumentSemanticModel document) {
    var semantics = document.semanticModel();
    return dev.w0fv1.norm.semantic.TestIndex.from(semantics).tests().stream()
        .map(test -> semantics.symbol(test.symbol()).orElseThrow())
        .filter(
            symbol -> symbol.declaration().orElseThrow().document().equals(document.source().id()))
        .map(
            symbol ->
                new TestDeclaration(
                    document.syntax().packageName().isEmpty()
                        ? symbol.name()
                        : document.syntax().packageName() + "." + symbol.name(),
                    symbol.declaration().orElseThrow()))
        .toList();
  }

  public record TestDeclaration(String name, SourceLocation location) {}

  public Optional<SignatureHelp> signatureHelp(AnalysisResult analysis, int offset) {
    return signatures.resolve(document(analysis), offset);
  }

  public Optional<SignatureHelp> signatureHelp(DocumentSemanticModel document, int offset) {
    return signatures.resolve(document, offset);
  }

  public Optional<HoverInfo> hover(AnalysisResult analysis, int offset) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> symbol = model.resolvedSymbolAt(offset);
    if (symbol.isPresent()) {
      Symbol value = SymbolPresentation.annotation(model, symbol.orElseThrow());
      if (value.kind() == SymbolKind.TYPE_PARAMETER && value.owner().isPresent()) {
        Optional<TypeParameterInfo> parameter =
            model.symbol(value.owner().orElseThrow()).stream()
                .flatMap(owner -> owner.typeParameters().stream())
                .filter(candidate -> candidate.type().identity().equals(value.type().identity()))
                .findFirst();
        if (parameter.isPresent()) {
          TypeParameterInfo info = parameter.orElseThrow();
          String signature =
              info.name()
                  + info.upperBound().map(bound -> " extends " + bound.displayName()).orElse("")
                  + info.defaultType().map(type -> " = " + type.displayName()).orElse("");
          return Optional.of(new HoverInfo("`" + signature + "`", value.declaration()));
        }
      }
      String signature = SymbolPresentation.signature(value);
      String markdown =
          value.documentation().isBlank()
              ? "`" + signature + "`"
              : "`" + signature + "`\n\n" + value.documentation();
      return Optional.of(new HoverInfo(markdown, value.declaration()));
    }
    return model
        .typeAt(offset)
        .map(SemanticType::displayName)
        .map(type -> new HoverInfo("`" + type + "`", Optional.empty()));
  }

  public Optional<SourceLocation> definition(AnalysisResult analysis, int offset) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> selected = model.resolvedSymbolAt(offset);
    if (selected.isEmpty()) return Optional.empty();
    Symbol symbol = selected.orElseThrow();
    boolean declaration =
        symbol
            .declaration()
            .filter(location -> location.document().equals(model.source().id()))
            .filter(location -> location.startOffset() <= offset && offset < location.endOffset())
            .isPresent();
    if (declaration) {
      Optional<SourceLocation> requirement =
          model.overriddenMembers(symbol).stream()
              .map(Symbol::declaration)
              .flatMap(Optional::stream)
              .findFirst();
      if (requirement.isPresent()) return requirement;
    }
    return symbol.declaration();
  }

  public List<SourceLocation> references(
      AnalysisResult analysis, int offset, boolean includeDeclaration) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> selected = model.symbolAt(offset);
    if (selected.isEmpty()) return List.of();
    Symbol symbol = selected.orElseThrow();
    Optional<SourceLocation> declaration = symbol.declaration();
    List<SourceSpan> references;
    if (model.isAlias(symbol.id())) {
      references = model.authoringReferences(symbol.id());
    } else {
      references =
          model.relatedMembers(symbol).stream()
              .flatMap(related -> model.references(related.id()).stream())
              .distinct()
              .toList();
    }
    return references.stream()
        .map(span -> span.location())
        .filter(location -> includeDeclaration || !declaration.equals(Optional.of(location)))
        .toList();
  }

  public Optional<RenameTarget> prepareRename(AnalysisResult analysis, int offset) {
    SemanticModel model = analysis.semanticModel();
    Optional<Symbol> symbol = model.symbolAt(offset);
    if (symbol.isEmpty()) return Optional.empty();
    List<Symbol> related =
        model.isAlias(symbol.orElseThrow().id())
            ? List.of(symbol.orElseThrow())
            : model.relatedMembers(symbol.orElseThrow());
    if (related.stream().anyMatch(candidate -> !isEditable(candidate))) return Optional.empty();
    return model
        .referenceAt(offset)
        .filter(span -> !model.isDeclarationOperator(span))
        .map(reference -> new RenameTarget(reference.location(), symbol.orElseThrow().name()));
  }

  public Optional<RenameEdit> rename(AnalysisResult analysis, int offset, String newName) {
    if (!LanguageSyntax.isIdentifier(newName)) {
      throw new IllegalArgumentException("rename target must be a valid Norm identifier");
    }
    SemanticModel model = analysis.semanticModel();
    if (model.referenceAt(offset).filter(model::isDeclarationOperator).isPresent())
      return Optional.empty();
    Optional<Symbol> selected = model.symbolAt(offset);
    if (selected.isEmpty()) return Optional.empty();
    List<Symbol> related =
        model.isAlias(selected.orElseThrow().id())
            ? List.of(selected.orElseThrow())
            : model.relatedMembers(selected.orElseThrow());
    if (related.stream().anyMatch(candidate -> !isEditable(candidate))) return Optional.empty();
    if (related.stream().anyMatch(symbol -> model.hasRenameConflict(symbol.id(), newName))) {
      throw new IllegalArgumentException(
          "name '" + newName + "' is already declared in this scope");
    }
    List<SourceLocation> locations =
        related.stream()
            .flatMap(symbol -> model.authoringReferences(symbol.id()).stream())
            .distinct()
            .map(span -> span.location())
            .toList();
    return Optional.of(new RenameEdit(newName, locations));
  }

  private static boolean isEditable(Symbol symbol) {
    return symbol.declaration().isPresent()
        && !symbol.declaration().orElseThrow().document().uri().getScheme().equals("stdlib");
  }

  private static DocumentSemanticModel document(AnalysisResult analysis) {
    SemanticModel model = analysis.semanticModel();
    return new DocumentSemanticModel(model.source(), model.syntax(), model.tokens(), model);
  }
}
