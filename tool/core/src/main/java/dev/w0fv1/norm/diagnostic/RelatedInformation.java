package dev.w0fv1.norm.diagnostic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.Objects;

public record RelatedInformation(SourceSpan span, String message) {
  public RelatedInformation {
    Objects.requireNonNull(span, "span");
    Objects.requireNonNull(message, "message");
    if (message.isBlank()) {
      throw new IllegalArgumentException("related information message must not be blank");
    }
  }
}
