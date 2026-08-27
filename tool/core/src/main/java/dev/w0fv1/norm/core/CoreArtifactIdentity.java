package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

final class CoreArtifactIdentity {
  private static final String SOURCE_DOMAIN = "norm:source:v1\0";

  private CoreArtifactIdentity() {}

  static byte[] code(CoreArtifact artifact) {
    CanonicalWriter writer = new CanonicalWriter().writeTag("core-code");
    writer.writeInt(artifact.program().groups().size());
    artifact.program().groups().forEach(group -> writer.writeBytes(group.id().hash().bytes()));
    writeDefinition(writer, artifact.entryDefinition());
    return writer.toByteArray();
  }

  static byte[] linkage(CoreArtifact artifact) {
    CanonicalWriter writer =
        new CanonicalWriter()
            .writeTag("linkage")
            .writeBytes(artifact.namespace().id().hash().bytes());
    var bindings =
        artifact.namespace().bindings().stream()
            .map(
                binding ->
                    new ArtifactBinding(
                        CoreNamespace.canonicalBinding(binding), binding.occurrence()))
            .sorted(
                (left, right) -> {
                  int bindingOrder =
                      Arrays.compareUnsigned(left.canonicalBinding(), right.canonicalBinding());
                  return bindingOrder != 0
                      ? bindingOrder
                      : left.occurrence().compareTo(right.occurrence());
                })
            .toList();
    writer.writeInt(bindings.size());
    bindings.forEach(
        binding -> {
          writer.writeBytes(binding.canonicalBinding());
          writeOccurrence(writer, binding.occurrence());
        });
    return writer.toByteArray();
  }

  static byte[] debug(CoreArtifact artifact) {
    CanonicalWriter writer = new CanonicalWriter().writeTag("debug-info");
    Map<String, SourceFile> documents = documents(artifact);
    Map<String, Integer> documentIndexes = new HashMap<>();
    writer.writeInt(documents.size());
    int documentIndex = 0;
    for (var entry : documents.entrySet()) {
      documentIndexes.put(entry.getKey(), documentIndex++);
      byte[] text = new CanonicalWriter().writeString(entry.getValue().text()).toByteArray();
      writer
          .writeString(entry.getKey())
          .writeBytes(ContentHasher.hash(SOURCE_DOMAIN, CoreIdentityVersion.CURRENT, text).bytes());
    }
    writer.writeInt(artifact.authoring().occurrences().size());
    artifact
        .authoring()
        .occurrences()
        .forEach(
            occurrence -> {
              writeOccurrence(writer, occurrence.id());
              writer.writeTag(occurrence.role().name());
              writer.writeInt(occurrence.representedDefinitions().size());
              occurrence.representedDefinitions().forEach(value -> writeDefinition(writer, value));
              writer.writeBytes(encodeOrigin(occurrence.origin(), documentIndexes));
              writer.writeInt(occurrence.references().size());
              occurrence.references().entrySet().stream()
                  .sorted(Map.Entry.comparingByKey())
                  .forEach(
                      reference -> {
                        writer.writeInt(reference.getKey());
                        writeOccurrence(writer, reference.getValue());
                      });
            });
    return writer.toByteArray();
  }

  static byte[] metadata(CoreArtifact artifact) {
    CanonicalWriter writer = new CanonicalWriter().writeTag("metadata");
    writer.writeInt(artifact.metadata().annotations().size());
    artifact
        .metadata()
        .annotations()
        .forEach(value -> writer.writeBytes(CoreCodec.encodeAnnotationApplication(value)));
    return writer.toByteArray();
  }

  static void writeDefinition(CanonicalWriter writer, DefinitionId definition) {
    writer.writeBytes(definition.group().hash().bytes()).writeInt(definition.memberIndex());
  }

  static void writeOccurrence(CanonicalWriter writer, DefinitionOccurrenceId occurrence) {
    writeDefinition(writer, occurrence.representative());
    writer.writeInt(occurrence.ordinal());
  }

  private static byte[] encodeOrigin(
      CoreDefinitionOrigin origin, Map<String, Integer> documentIndexes) {
    CanonicalWriter writer = new CanonicalWriter();
    writeSpanDocument(
            writer.writeString(origin.definitionName()), origin.rootSpan(), documentIndexes)
        .writeInt(origin.rootSpan().startOffset())
        .writeInt(origin.rootSpan().length())
        .writeInt(origin.nodeSpans().size());
    origin.nodeSpans().entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                writeSpanDocument(
                        writer.writeInt(entry.getKey()), entry.getValue(), documentIndexes)
                    .writeInt(entry.getValue().startOffset())
                    .writeInt(entry.getValue().length()));
    return writer.toByteArray();
  }

  private static CanonicalWriter writeSpanDocument(
      CanonicalWriter writer, SourceSpan span, Map<String, Integer> documentIndexes) {
    Integer index = documentIndexes.get(span.source().id().uri().toString());
    if (index == null) throw new IllegalArgumentException("source span document is absent");
    return writer.writeInt(index);
  }

  private static Map<String, SourceFile> documents(CoreArtifact artifact) {
    Map<String, SourceFile> documents = new TreeMap<>();
    artifact.authoring().occurrences().stream()
        .map(CoreDefinitionOccurrence::origin)
        .forEach(
            origin -> {
              addDocument(documents, origin.rootSpan().source());
              origin.nodeSpans().values().forEach(span -> addDocument(documents, span.source()));
            });
    return documents;
  }

  private static void addDocument(Map<String, SourceFile> documents, SourceFile source) {
    String uri = source.id().uri().toString();
    SourceFile existing = documents.putIfAbsent(uri, source);
    if (existing != null && !existing.text().equals(source.text())) {
      throw new IllegalArgumentException("source document has conflicting content");
    }
  }

  private record ArtifactBinding(byte[] canonicalBinding, DefinitionOccurrenceId occurrence) {}
}
