package dev.w0fv1.norm.language;

import dev.w0fv1.norm.source.SourceSpan;
import java.util.Objects;
import java.util.Optional;

public record MemberAccessSite(
    SourceSpan receiver, SourceSpan name, Form form, Optional<SourceSpan> openingBlock) {
  public enum Form {
    EXPLICIT,
    BLOCK_CONTINUATION
  }

  public MemberAccessSite {
    Objects.requireNonNull(receiver, "receiver");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(form, "form");
    openingBlock = Objects.requireNonNull(openingBlock, "openingBlock");
    if (receiver.isEmpty()
        || !receiver.source().equals(name.source())
        || receiver.endOffset() > name.startOffset()) {
      throw new IllegalArgumentException(
          "member ranges must belong to one source snapshot in order");
    }
    if (openingBlock.isPresent()) {
      SourceSpan opening = openingBlock.orElseThrow();
      if (!opening.source().equals(name.source())
          || opening.startOffset() < name.endOffset()
          || !opening.text().equals("{")) {
        throw new IllegalArgumentException(
            "member block must follow its name in the same snapshot");
      }
    }
  }
}
