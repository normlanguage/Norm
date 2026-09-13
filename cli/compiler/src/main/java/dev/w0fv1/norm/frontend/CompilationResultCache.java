package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.core.CoreCompilationDelta;
import dev.w0fv1.norm.core.IncrementalAnalysisReport;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

final class CompilationResultCache {
  private final dev.w0fv1.norm.core.store.FileArtifactCache artifacts;
  private final LanguageProfile profile;
  private final String compilerIdentity;

  CompilationResultCache(Path directory, LanguageProfile profile) throws IOException {
    this.artifacts =
        new dev.w0fv1.norm.core.store.FileArtifactCache(directory, 128, 512L * 1024 * 1024);
    this.profile = profile;
    compilerIdentity = dev.w0fv1.norm.core.store.CompilerArtifactIdentity.current();
  }

  Sha256Digest key(CompilationRequest request) {
    var writer =
        new CanonicalWriter()
            .writeTag("compilation-result-1")
            .writeString(compilerIdentity)
            .writeString(profile.identityVersion().storageNamespace())
            .writeString(request.unit().toString())
            .writeString(request.entryDocument().uri().toString());
    sources(writer, request.sources());
    scope(writer, request.scope());
    documents(writer, request.exportedSources());
    documents(writer, request.bindingSources());
    sources(writer, profile.prelude().documents().stream().map(ParsedDocument::source).toList());
    documents(writer, profile.prelude().exportedSources());
    writer.writeBoolean(profile.prelude().scope().isPresent());
    profile.prelude().scope().ifPresent(value -> scope(writer, value));
    documents(writer, profile.moduleEvaluationDocuments());
    documents(writer, profile.standardLibraryDocuments());
    return Sha256Digest.compute(writer.toByteArray());
  }

  Optional<CompilationResult> read(Sha256Digest key) throws IOException {
    var stored = artifacts.read(key);
    if (stored.isEmpty()) return Optional.empty();
    byte[] payload = stored.orElseThrow();
    CompilationResult result = PortableObjectCodec.decode(payload, CompilationResult.class);
    var output = result.output().orElseThrow();
    var previous = output.state().buildReport();
    var metrics = previous.canonicalization();
    var report =
        new dev.w0fv1.norm.core.CoreBuildReport(
            previous.definitions(),
            previous.groups(),
            0,
            previous.groups(),
            0,
            new dev.w0fv1.norm.core.CoreCanonicalizationMetrics(
                metrics.components(), metrics.maximumComponentSize(), 0, 0, 0, 0));
    return Optional.of(
        new CompilationResult(
            Optional.of(
                new dev.w0fv1.norm.core.CompilationOutput(
                    output.artifact(),
                    new dev.w0fv1.norm.core.CompilationState(
                        report,
                        output.state().dependencies(),
                        CoreCompilationDelta.initial(output.artifact().program()),
                        IncrementalAnalysisReport.reused(
                            output.state().analysisReport().declarations())))),
            result.diagnostics()));
  }

  void write(Sha256Digest key, CompilationResult result) throws IOException {
    if (!result.isSuccess()) return;
    byte[] payload = PortableObjectCodec.encode(result);
    if (payload.length > 64 * 1024 * 1024) return;
    artifacts.write(key, payload);
  }

  private static void sources(CanonicalWriter writer, java.util.List<SourceFile> sources) {
    writer.writeInt(sources.size());
    sources.stream()
        .sorted(Comparator.comparing(source -> source.id().uri().toString()))
        .forEach(
            source -> writer.writeString(source.id().uri().toString()).writeString(source.text()));
  }

  private static void documents(CanonicalWriter writer, Set<DocumentId> documents) {
    writer.writeInt(documents.size());
    documents.stream()
        .map(document -> document.uri().toString())
        .sorted()
        .forEach(writer::writeString);
  }

  private static void scope(CanonicalWriter writer, CompilationScope scope) {
    writer.writeInt(scope.coordinates().size());
    scope
        .coordinates()
        .forEach(
            (document, coordinate) ->
                writer
                    .writeString(document.uri().toString())
                    .writeString(coordinate.module().name())
                    .writeInt(coordinate.module().version())
                    .writeString(coordinate.relativePath()));
    writer.writeInt(scope.modules().dependencies().size());
    scope
        .modules()
        .dependencies()
        .forEach(
            (module, dependencies) -> {
              writer
                  .writeString(module.name())
                  .writeInt(module.version())
                  .writeInt(dependencies.size());
              dependencies.forEach(
                  dependency ->
                      writer.writeString(dependency.name()).writeInt(dependency.version()));
            });
    documents(writer, scope.testSources());
  }
}
