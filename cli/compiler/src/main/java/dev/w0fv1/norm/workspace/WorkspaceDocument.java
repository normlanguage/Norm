package dev.w0fv1.norm.workspace;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.semantic.AnalysisResult;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import java.util.Set;

public record WorkspaceDocument(
    int version,
    String clientUri,
    SourceFile source,
    AnalysisResult analysis,
    Path projectRoot,
    Set<Path> sourcePaths,
    long revision,
    CompilationSnapshot snapshot) {
  public WorkspaceDocument {
    sourcePaths = Set.copyOf(sourcePaths);
  }
}
