package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceLocation;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record IncrementalAnalysisPlan(
    Map<SourceSpan, SemanticContribution> reusable, int declarations, int reusedDeclarations) {
  IncrementalAnalysisPlan {
    reusable = Map.copyOf(reusable);
  }

  static IncrementalAnalysisPlan full(List<ParsedDocument> documents) {
    return new IncrementalAnalysisPlan(Map.of(), declarations(documents).size(), 0);
  }

  static IncrementalAnalysisPlan create(
      CompilationSnapshot previous, List<ParsedDocument> currentDocuments) {
    if (previous == null || previous.analysis().hasErrors()) return full(currentDocuments);
    List<DeclarationRef> current = declarations(currentDocuments);
    List<DeclarationRef> old = declarations(previous);
    Map<String, DeclarationRef> currentByKey = byKey(current);
    Map<String, DeclarationRef> oldByKey = byKey(old);
    Map<DocumentId, DocumentContext> currentContexts = contexts(currentDocuments);
    Map<DocumentId, DocumentContext> oldContexts = contexts(previous);

    Set<String> affected = new LinkedHashSet<>();
    Set<String> allKeys = new LinkedHashSet<>(oldByKey.keySet());
    allKeys.addAll(currentByKey.keySet());
    for (String key : allKeys) {
      DeclarationRef currentDeclaration = currentByKey.get(key);
      DeclarationRef oldDeclaration = oldByKey.get(key);
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
    }

    SemanticModel model = previous.semanticModel();
    Map<String, Set<String>> dependents = new LinkedHashMap<>();
    for (DeclarationRef declaration : old) {
      for (SourceLocation dependency : model.declarationDependencies(declaration.span())) {
        String target = containing(old, dependency);
        if (target != null && !target.equals(declaration.key())) {
          dependents
              .computeIfAbsent(target, ignored -> new LinkedHashSet<>())
              .add(declaration.key());
        }
      }
    }
    ArrayDeque<String> pending = new ArrayDeque<>(affected);
    while (!pending.isEmpty()) {
      String changed = pending.removeFirst();
      for (String dependent : dependents.getOrDefault(changed, Set.of())) {
        if (affected.add(dependent)) pending.addLast(dependent);
      }
    }

    Map<SourceSpan, SemanticContribution> reusable = new LinkedHashMap<>();
    for (String key : currentByKey.keySet()) {
      if (affected.contains(key)) continue;
      DeclarationRef currentDeclaration = currentByKey.get(key);
      DeclarationRef oldDeclaration = oldByKey.get(key);
      reusable.put(
          currentDeclaration.span(),
          model.contribution(
              oldDeclaration.span(),
              currentDeclaration.span(),
              oldDeclaration.tokens(),
              currentDeclaration.tokens()));
    }
    return new IncrementalAnalysisPlan(reusable, current.size(), reusable.size());
  }

  int analyzedDeclarations() {
    return declarations - reusedDeclarations;
  }

  private static List<DeclarationRef> declarations(List<ParsedDocument> documents) {
    List<DeclarationRef> declarations = new ArrayList<>();
    for (ParsedDocument document : documents) {
      add(declarations, document.syntax(), document.tokens(), document.syntax().enums());
      add(declarations, document.syntax(), document.tokens(), document.syntax().interfaces());
      add(declarations, document.syntax(), document.tokens(), document.syntax().aggregates());
      add(declarations, document.syntax(), document.tokens(), document.syntax().functions());
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
              add(declarations, syntax, model.tokens(), syntax.enums());
              add(declarations, syntax, model.tokens(), syntax.interfaces());
              add(declarations, syntax, model.tokens(), syntax.aggregates());
              add(declarations, syntax, model.tokens(), syntax.functions());
            });
    return List.copyOf(declarations);
  }

  private static <T> void add(
      List<DeclarationRef> result,
      Syntax.Program program,
      List<Token> documentTokens,
      List<T> declarations) {
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
      DeclarationIdentity identity = DeclarationIdentity.topLevel(program, declaration);
      List<Token> tokens = tokensInside(documentTokens, span);
      result.add(
          new DeclarationRef(identity.value(), identity.family(), span, tokens, structure(tokens)));
    }
  }

  private static List<Token> tokensInside(List<Token> tokens, SourceSpan root) {
    return tokens.stream()
        .filter(token -> token.kind() != TokenKind.END_OF_FILE)
        .filter(token -> token.span().source().id().equals(root.source().id()))
        .filter(token -> token.span().startOffset() >= root.startOffset())
        .filter(token -> token.span().endOffset() <= root.endOffset())
        .toList();
  }

  private static List<TokenShape> structure(List<Token> tokens) {
    return tokens.stream()
        .map(token -> new TokenShape(token.kind().name(), token.lexeme()))
        .toList();
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

  private static String containing(List<DeclarationRef> declarations, SourceLocation location) {
    return declarations.stream()
        .filter(declaration -> declaration.span().source().id().equals(location.document()))
        .filter(
            declaration ->
                declaration.span().startOffset() <= location.startOffset()
                    && location.startOffset() < declaration.span().endOffset())
        .map(DeclarationRef::key)
        .findFirst()
        .orElse(null);
  }

  private record DeclarationRef(
      String key, String family, SourceSpan span, List<Token> tokens, List<TokenShape> structure) {
    private DeclarationRef {
      tokens = List.copyOf(tokens);
      structure = List.copyOf(structure);
    }
  }

  private record TokenShape(String kind, String lexeme) {}

  private record DocumentContext(String packageName, List<ImportContext> imports) {
    private DocumentContext {
      imports = List.copyOf(imports);
    }
  }

  private record ImportContext(String qualifiedName, java.util.Optional<String> alias) {}
}
