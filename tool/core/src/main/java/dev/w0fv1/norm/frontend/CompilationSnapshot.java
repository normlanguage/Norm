package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.DocumentId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CompilationSnapshot {
  private final DocumentId entryDocument;
  private final Map<DocumentId, DocumentSemanticModel> documents;
  private final AnalysisResult analysis;

  CompilationSnapshot(
      DocumentId entryDocument, List<ParsedDocument> parsedDocuments, AnalysisResult analysis) {
    this.entryDocument = java.util.Objects.requireNonNull(entryDocument, "entryDocument");
    AnalysisResult analyzed = java.util.Objects.requireNonNull(analysis, "analysis");
    ParsedDocument entry =
        parsedDocuments.stream()
            .filter(parsed -> parsed.source().id().equals(entryDocument))
            .findFirst()
            .orElseThrow();
    SemanticModel projectModel =
        analyzed.semanticModel().documentView(entry.source(), entry.syntax(), entry.tokens());
    this.analysis =
        new AnalysisResult(
            projectModel, analyzed.entryPoint(), analyzed.boundProgram(), analyzed.diagnostics());
    Map<DocumentId, DocumentSemanticModel> views = new LinkedHashMap<>();
    parsedDocuments.forEach(
        parsed ->
            views.put(
                parsed.source().id(),
                new DocumentSemanticModel(
                    parsed.source(), parsed.syntax(), parsed.tokens(), projectModel)));
    documents = Map.copyOf(views);
  }

  public DocumentId entryDocumentId() {
    return entryDocument;
  }

  public DocumentSemanticModel entryDocument() {
    return document(entryDocument).orElseThrow();
  }

  public Optional<DocumentSemanticModel> document(DocumentId document) {
    return Optional.ofNullable(documents.get(document));
  }

  public Set<DocumentId> documentIds() {
    return documents.keySet();
  }

  public SemanticModel semanticModel() {
    return analysis.semanticModel();
  }

  public List<Diagnostic> diagnostics() {
    return analysis.diagnostics();
  }

  public List<Diagnostic> diagnostics(DocumentId document) {
    return document(document).map(DocumentSemanticModel::diagnostics).orElse(List.of());
  }

  public AnalysisResult analysis(DocumentId document) {
    DocumentSemanticModel model = document(document).orElseThrow();
    return new AnalysisResult(
        model.semanticModel(),
        analysis.entryPoint(),
        analysis.boundProgram(),
        analysis.diagnostics());
  }

  public AnalysisResult analysis() {
    return analysis(entryDocument);
  }
}
