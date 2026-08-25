package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.syntax.Syntax;
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
    if (!currentByKey.keySet().equals(oldByKey.keySet())) return full(currentDocuments);
    Map<DocumentId, DocumentContext> currentContexts = contexts(currentDocuments);
    Map<DocumentId, DocumentContext> oldContexts = contexts(previous);
    if (!currentContexts.keySet().equals(oldContexts.keySet())) return full(currentDocuments);

    Set<String> affected = new LinkedHashSet<>();
    for (String key : currentByKey.keySet()) {
      SourceSpan currentSpan = currentByKey.get(key).span();
      SourceSpan oldSpan = oldByKey.get(key).span();
      if (!currentContexts
              .get(currentSpan.source().id())
              .equals(oldContexts.get(oldSpan.source().id()))
          || currentSpan.startOffset() != oldSpan.startOffset()
          || currentSpan.endOffset() != oldSpan.endOffset()
          || !currentSpan.text().equals(oldSpan.text())) {
        affected.add(key);
      }
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
          model.contribution(oldDeclaration.span(), currentDeclaration.span().source()));
    }
    return new IncrementalAnalysisPlan(reusable, current.size(), reusable.size());
  }

  int analyzedDeclarations() {
    return declarations - reusedDeclarations;
  }

  private static List<DeclarationRef> declarations(List<ParsedDocument> documents) {
    List<DeclarationRef> declarations = new ArrayList<>();
    for (ParsedDocument document : documents) {
      add(declarations, document.source().id(), "enum", document.syntax().enums());
      add(declarations, document.source().id(), "interface", document.syntax().interfaces());
      add(declarations, document.source().id(), "class", document.syntax().classes());
      add(declarations, document.source().id(), "function", document.syntax().functions());
    }
    return List.copyOf(declarations);
  }

  private static List<DeclarationRef> declarations(CompilationSnapshot snapshot) {
    List<DeclarationRef> declarations = new ArrayList<>();
    snapshot.documentIds().stream()
        .sorted(java.util.Comparator.comparing(document -> document.uri().toString()))
        .forEach(
            document -> {
              Syntax.Program syntax = snapshot.document(document).orElseThrow().syntax();
              add(declarations, document, "enum", syntax.enums());
              add(declarations, document, "interface", syntax.interfaces());
              add(declarations, document, "class", syntax.classes());
              add(declarations, document, "function", syntax.functions());
            });
    return List.copyOf(declarations);
  }

  private static <T> void add(
      List<DeclarationRef> result, DocumentId document, String kind, List<T> declarations) {
    for (int index = 0; index < declarations.size(); index++) {
      T declaration = declarations.get(index);
      SourceSpan span;
      String name;
      if (declaration instanceof Syntax.EnumDecl value) {
        span = value.span();
        name = value.name();
      } else if (declaration instanceof Syntax.InterfaceDecl value) {
        span = value.span();
        name = value.name();
      } else if (declaration instanceof Syntax.ClassDecl value) {
        span = value.span();
        name = value.name();
      } else if (declaration instanceof Syntax.FunctionDecl value) {
        span = value.span();
        name = value.name();
      } else {
        throw new IllegalStateException("unsupported top-level declaration");
      }
      result.add(new DeclarationRef(document.uri() + "/" + kind + "/" + index + "/" + name, span));
    }
  }

  private static Map<String, DeclarationRef> byKey(List<DeclarationRef> declarations) {
    Map<String, DeclarationRef> result = new LinkedHashMap<>();
    declarations.forEach(declaration -> result.put(declaration.key(), declaration));
    return Map.copyOf(result);
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

  private record DeclarationRef(String key, SourceSpan span) {}

  private record DocumentContext(String packageName, List<ImportContext> imports) {
    private DocumentContext {
      imports = List.copyOf(imports);
    }
  }

  private record ImportContext(String qualifiedName, java.util.Optional<String> alias) {}
}
