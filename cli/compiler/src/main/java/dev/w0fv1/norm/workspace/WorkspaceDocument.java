package dev.w0fv1.norm.workspace;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.project.ProjectInputSnapshot;
import dev.w0fv1.norm.semantic.AnalysisResult;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;

public record WorkspaceDocument(
    int version,
    String clientUri,
    SourceFile source,
    AnalysisResult analysis,
    Path projectRoot,
    ProjectInputSnapshot inputs,
    long revision,
    CompilationSnapshot snapshot) {}
