package dev.w0fv1.norm.language;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record DocumentRevision(DocumentId document, Sha256Digest content) {
  public DocumentRevision {
    Objects.requireNonNull(document, "document");
    Objects.requireNonNull(content, "content");
  }

  public static DocumentRevision of(SourceFile source) {
    return new DocumentRevision(
        source.id(), Sha256Digest.compute(source.text().getBytes(StandardCharsets.UTF_8)));
  }
}
