package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceLocation;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.BlockCallChainSyntax;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.syntax.TokenSpanMapping;
import dev.w0fv1.norm.value.CompilationScope;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record IncrementalAnalysisPlan(
    Map<SourceSpan, SemanticContribution> reusable,
    Map<SourceSpan, TokenSpanMapping> mappings,
    int declarations,
    int reusedDeclarations) {
  IncrementalAnalysisPlan {
    reusable = Map.copyOf(reusable);
    mappings = Map.copyOf(mappings);
  }

  static IncrementalAnalysisPlan create(
      History previous,
      List<ParsedDocument> currentDocuments,
      CompilationScope scope,
      DeclarationAnalysis declarations) {
    List<DeclarationRef> current = declarations(currentDocuments, scope);
    if (previous == null) return new IncrementalAnalysisPlan(Map.of(), Map.of(), current.size(), 0);
    List<DeclarationRef> old = previous.declarations();
    Map<String, DeclarationRef> currentByKey = byKey(current);
    Map<String, DeclarationRef> oldByKey = byKey(old);
    Map<DocumentId, DocumentContext> currentContexts = contexts(currentDocuments);
    Map<DocumentId, DocumentContext> oldContexts = previous.contexts();
    var currentContracts =
        declarations.contracts(current.stream().map(DeclarationRef::span).toList());

    Set<String> affected = new LinkedHashSet<>();
    Set<String> changedContracts = new LinkedHashSet<>();
    Set<String> allKeys = new LinkedHashSet<>(oldByKey.keySet());
    allKeys.addAll(currentByKey.keySet());
    for (String key : allKeys) {
      DeclarationRef currentDeclaration = currentByKey.get(key);
      DeclarationRef oldDeclaration = oldByKey.get(key);
      if (currentDeclaration == null
          || oldDeclaration == null
          || !currentContracts
              .get(currentDeclaration.span())
              .equals(previous.contracts().get(key))) {
        affected.add(key);
        changedContracts.add(key);
      }
      if (currentDeclaration == null
          || oldDeclaration == null
          || !currentContexts
              .get(currentDeclaration.span().source().id())
              .equals(oldContexts.get(oldDeclaration.span().source().id()))
          || !currentDeclaration.structure().equals(oldDeclaration.structure())) {
        affected.add(key);
      }
    }
    Map<String, Set<String>> currentFamilies = byFamily(current);
    Map<String, Set<String>> oldFamilies = byFamily(old);
    Set<String> allFamilies = new LinkedHashSet<>(oldFamilies.keySet());
    allFamilies.addAll(currentFamilies.keySet());
    for (String family : allFamilies) {
      Set<String> currentMembers = currentFamilies.getOrDefault(family, Set.of());
      Set<String> oldMembers = oldFamilies.getOrDefault(family, Set.of());
      if (currentMembers.equals(oldMembers)) continue;
      affected.addAll(currentMembers);
      affected.addAll(oldMembers);
      changedContracts.addAll(currentMembers);
      changedContracts.addAll(oldMembers);
    }

    Map<String, Set<String>> dependents = previous.dependents();
    ArrayDeque<String> pending = new ArrayDeque<>(changedContracts);
    Set<String> propagated = new LinkedHashSet<>(changedContracts);
    while (!pending.isEmpty()) {
      String changed = pending.removeFirst();
      for (String dependent : dependents.getOrDefault(changed, Set.of())) {
        if (propagated.add(dependent)) {
          affected.add(dependent);
          pending.addLast(dependent);
        }
      }
    }

    Map<SourceSpan, SemanticContribution> reusable = new LinkedHashMap<>();
    Map<SourceSpan, TokenSpanMapping> mappings = new LinkedHashMap<>();
    for (String key : currentByKey.keySet()) {
      if (affected.contains(key)) continue;
      DeclarationRef currentDeclaration = currentByKey.get(key);
      DeclarationRef oldDeclaration = oldByKey.get(key);
      var mapping =
          new TokenSpanMapping(
              oldDeclaration.span(),
              currentDeclaration.span(),
              oldDeclaration.tokens(),
              currentDeclaration.tokens());
      mappings.put(oldDeclaration.span(), mapping);
      reusable.put(currentDeclaration.span(), previous.contributions().get(key).rebase(mapping));
    }
    return new IncrementalAnalysisPlan(reusable, mappings, current.size(), reusable.size());
  }

  static History capture(CompilationSnapshot previous) {
    if (previous == null || previous.analysis().hasErrors()) return null;
    List<DeclarationRef> old = declarations(previous);
    SemanticModel model = previous.semanticModel();
    var ownership =
        dev.w0fv1.norm.semantic.SpanIndex.of(
            old.stream()
                .map(
                    declaration ->
                        new dev.w0fv1.norm.semantic.SpanIndex.Entry<>(
                            declaration.span(), declaration.key()))
                .toList());
    var facts = model.contributions(old.stream().map(DeclarationRef::span).toList());
    Map<String, Set<String>> dependents = new LinkedHashMap<>();
    for (DeclarationRef declaration : old) {
      for (SourceLocation dependency :
          model.declarationDependencies(facts.get(declaration.span()))) {
        String target =
            ownership
                .at(dependency.document(), dependency.startOffset())
                .map(dev.w0fv1.norm.semantic.SpanIndex.Entry::value)
                .orElse(null);
        if (target != null && !target.equals(declaration.key())) {
          dependents
              .computeIfAbsent(target, ignored -> new LinkedHashSet<>())
              .add(declaration.key());
        }
      }
    }
    Map<String, SemanticContribution> contributions = new LinkedHashMap<>();
    for (var declaration : old) contributions.put(declaration.key(), facts.get(declaration.span()));
    var captured =
        previous.declarations().contracts(old.stream().map(DeclarationRef::span).toList());
    Map<String, Map<dev.w0fv1.norm.semantic.SymbolId, DeclarationContract>> contracts =
        new LinkedHashMap<>();
    for (var declaration : old) contracts.put(declaration.key(), captured.get(declaration.span()));
    return new History(
        old,
        contexts(previous),
        contributions,
        contracts,
        dependents,
        model.nextSourceSymbolOrdinal());
  }

  record History(
      List<DeclarationRef> declarations,
      Map<DocumentId, DocumentContext> contexts,
      Map<String, SemanticContribution> contributions,
      Map<String, Map<dev.w0fv1.norm.semantic.SymbolId, DeclarationContract>> contracts,
      Map<String, Set<String>> dependents,
      int nextSymbolOrdinal) {
    Map<SourceSpan, SemanticContribution> contributions(Set<DocumentId> documents) {
      var result = new LinkedHashMap<SourceSpan, SemanticContribution>();
      for (var declaration : declarations) {
        if (documents.contains(declaration.span().source().id()))
          result.put(declaration.span(), contributions.get(declaration.key()));
      }
      return Map.copyOf(result);
    }

    History {
      declarations = List.copyOf(declarations);
      contexts = Map.copyOf(contexts);
      contributions = Map.copyOf(contributions);
      contracts =
          contracts.entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
      var copied = new LinkedHashMap<String, Set<String>>();
      dependents.forEach((key, values) -> copied.put(key, Set.copyOf(values)));
      dependents = Map.copyOf(copied);
    }
  }

  int analyzedDeclarations() {
    return declarations - reusedDeclarations;
  }

  Set<dev.w0fv1.norm.value.ModuleCoordinate> analyzedModules(
      List<ParsedDocument> documents, CompilationScope scope) {
    return declarations(documents, scope).stream()
        .filter(declaration -> !reusable.containsKey(declaration.span()))
        .map(declaration -> scope.coordinate(declaration.span().source().id()).module())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static List<DeclarationRef> declarations(
      List<ParsedDocument> documents, CompilationScope scope) {
    List<DeclarationRef> declarations = new ArrayList<>();
    for (ParsedDocument document : documents) {
      add(declarations, document.syntax(), document.tokens(), document.syntax().enums(), scope);
      add(
          declarations,
          document.syntax(),
          document.tokens(),
          document.syntax().interfaces(),
          scope);
      add(
          declarations,
          document.syntax(),
          document.tokens(),
          document.syntax().aggregates(),
          scope);
      add(declarations, document.syntax(), document.tokens(), document.syntax().functions(), scope);
    }
    return List.copyOf(declarations);
  }

  private static List<DeclarationRef> declarations(CompilationSnapshot snapshot) {
    List<DeclarationRef> declarations = new ArrayList<>();
    snapshot.documentIds().stream()
        .sorted(java.util.Comparator.comparing(document -> document.uri().toString()))
        .forEach(
            document -> {
              var model = snapshot.document(document).orElseThrow();
              Syntax.Program syntax = model.syntax();
              add(
                  declarations,
                  syntax,
                  model.tokens(),
                  syntax.enums(),
                  snapshot.semanticModel().compilationScope());
              add(
                  declarations,
                  syntax,
                  model.tokens(),
                  syntax.interfaces(),
                  snapshot.semanticModel().compilationScope());
              add(
                  declarations,
                  syntax,
                  model.tokens(),
                  syntax.aggregates(),
                  snapshot.semanticModel().compilationScope());
              add(
                  declarations,
                  syntax,
                  model.tokens(),
                  syntax.functions(),
                  snapshot.semanticModel().compilationScope());
            });
    return List.copyOf(declarations);
  }

  private static <T> void add(
      List<DeclarationRef> result,
      Syntax.Program program,
      List<Token> documentTokens,
      List<T> declarations,
      CompilationScope scope) {
    for (T declaration : declarations) {
      SourceSpan span;
      if (declaration instanceof Syntax.EnumDecl value) {
        span = value.span();
      } else if (declaration instanceof Syntax.InterfaceDecl value) {
        span = value.span();
      } else if (declaration instanceof Syntax.AggregateDecl value) {
        span = value.span();
      } else if (declaration instanceof Syntax.FunctionDecl value) {
        span = value.span();
      } else {
        throw new IllegalStateException("unsupported top-level declaration");
      }
      DeclarationIdentity identity =
          DeclarationIdentity.topLevel(
              program, declaration, scope.coordinate(program.span().source().id()));
      List<Token> tokens = tokensInside(documentTokens, span);
      result.add(
          new DeclarationRef(identity.value(), identity.family(), span, tokens, structure(tokens)));
    }
  }

  private static List<Token> tokensInside(List<Token> tokens, SourceSpan root) {
    int low = 0;
    int high = tokens.size();
    while (low < high) {
      int middle = (low + high) >>> 1;
      if (tokens.get(middle).span().startOffset() < root.startOffset()) low = middle + 1;
      else high = middle;
    }
    List<Token> selected = new ArrayList<>();
    for (int index = low; index < tokens.size(); index++) {
      Token token = tokens.get(index);
      if (token.span().startOffset() >= root.endOffset()) break;
      if (token.kind() != TokenKind.END_OF_FILE && token.span().endOffset() <= root.endOffset())
        selected.add(token);
    }
    return List.copyOf(selected);
  }

  private static List<TokenShape> structure(List<Token> tokens) {
    List<TokenShape> structure = new ArrayList<>(tokens.size());
    for (int index = 0; index < tokens.size(); index++) {
      Token token = tokens.get(index);
      structure.add(
          new TokenShape(token.kind(), token.lexeme(), BlockCallChainSyntax.isHead(tokens, index)));
    }
    return List.copyOf(structure);
  }

  private static Map<String, DeclarationRef> byKey(List<DeclarationRef> declarations) {
    Map<String, DeclarationRef> result = new LinkedHashMap<>();
    declarations.forEach(declaration -> result.put(declaration.key(), declaration));
    return Map.copyOf(result);
  }

  private static Map<String, Set<String>> byFamily(List<DeclarationRef> declarations) {
    Map<String, Set<String>> result = new LinkedHashMap<>();
    declarations.forEach(
        declaration ->
            result
                .computeIfAbsent(declaration.family(), ignored -> new LinkedHashSet<>())
                .add(declaration.key()));
    Map<String, Set<String>> copied = new LinkedHashMap<>();
    result.forEach((family, members) -> copied.put(family, Set.copyOf(members)));
    return Map.copyOf(copied);
  }

  private static Map<DocumentId, DocumentContext> contexts(List<ParsedDocument> documents) {
    Map<DocumentId, DocumentContext> result = new LinkedHashMap<>();
    documents.forEach(document -> result.put(document.source().id(), context(document.syntax())));
    return Map.copyOf(result);
  }

  private static Map<DocumentId, DocumentContext> contexts(CompilationSnapshot snapshot) {
    Map<DocumentId, DocumentContext> result = new LinkedHashMap<>();
    snapshot.documentIds().stream()
        .sorted(java.util.Comparator.comparing(document -> document.uri().toString()))
        .forEach(
            document ->
                result.put(document, context(snapshot.document(document).orElseThrow().syntax())));
    return Map.copyOf(result);
  }

  private static DocumentContext context(Syntax.Program program) {
    return new DocumentContext(
        program.packageName(),
        program.imports().stream()
            .map(imported -> new ImportContext(imported.qualifiedName(), imported.alias()))
            .toList());
  }

  private record DeclarationRef(
      String key, String family, SourceSpan span, List<Token> tokens, List<TokenShape> structure) {
    private DeclarationRef {
      tokens = List.copyOf(tokens);
      structure = List.copyOf(structure);
    }
  }

  private record TokenShape(TokenKind kind, String lexeme, boolean blockContinuation) {}

  private record DocumentContext(String packageName, List<ImportContext> imports) {
    private DocumentContext {
      imports = List.copyOf(imports);
    }
  }

  private record ImportContext(String qualifiedName, java.util.Optional<String> alias) {}
}
