package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.core.CanonicalWriter;
import dev.w0fv1.norm.core.CompilationResult;
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
  private final dev.w0fv1.norm.core.store.FileArtifactCache histories;
  private final LanguageProfile profile;
  private final String compilerIdentity;

  CompilationResultCache(Path directory, LanguageProfile profile) throws IOException {
    this.artifacts =
        new dev.w0fv1.norm.core.store.FileArtifactCache(directory, 128, 512L * 1024 * 1024);
    this.histories =
        new dev.w0fv1.norm.core.store.FileArtifactCache(
            directory.resolve("history"), 128, 512L * 1024 * 1024);
    this.profile = profile;
    compilerIdentity = dev.w0fv1.norm.core.store.CompilerArtifactIdentity.current();
  }

  Sha256Digest key(CompilationRequest request) {
    return key(request, java.util.List.of());
  }

  Sha256Digest key(CompilationRequest request, java.util.List<Sha256Digest> modules) {
    return key(request, modules, true);
  }

  private Sha256Digest key(
      CompilationRequest request, java.util.List<Sha256Digest> modules, boolean includeContent) {
    var writer =
        new CanonicalWriter()
            .writeTag(includeContent ? "compilation-result-2" : "compilation-history-1")
            .writeString(compilerIdentity)
            .writeString(profile.identityVersion().storageNamespace())
            .writeString(request.unit().toString())
            .writeString(request.kind().name())
            .writeString(request.entryDocument().uri().toString());
    writer.writeInt(modules.size());
    modules.forEach(module -> writer.writeString(module.value()));
    if (includeContent) sources(writer, request.sources());
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

  Optional<CompilationHistory> readHistory(
      CompilationRequest request, java.util.List<Sha256Digest> modules) throws IOException {
    var stored = histories.read(key(request, modules, false));
    if (stored.isEmpty()) return Optional.empty();
    return Optional.of(PortableObjectCodec.decode(stored.orElseThrow(), CompilationHistory.class));
  }

  void writeHistory(
      CompilationRequest request, java.util.List<Sha256Digest> modules, CompilationHistory history)
      throws IOException {
    if (history == null) return;
    byte[] payload = PortableObjectCodec.encode(history);
    if (payload.length <= 64 * 1024 * 1024) histories.write(key(request, modules, false), payload);
  }

  Optional<CompilationResult> read(Sha256Digest key) throws IOException {
    var stored = artifacts.read(key);
    if (stored.isEmpty()) return Optional.empty();
    byte[] payload = stored.orElseThrow();
    CompilationResult result = PortableObjectCodec.decode(payload, CompilationResult.class);
    return Optional.of(
        new CompilationResult(
            Optional.of(result.output().orElseThrow().reused()), result.diagnostics()));
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
