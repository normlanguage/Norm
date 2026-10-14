package dev.w0fv1.norm.core;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;

public final class CoreDefinitionContent {
  private final CoreDefinition definition;
  private final int slots;
  private final byte[] canonicalBytes;
  private final CoreContentId id;

  public CoreDefinitionContent(CoreDefinition definition) {
    this.definition = Objects.requireNonNull(definition, "definition");
    var links = CoreTree.links(definition);
    var indices = new LinkedHashSet<Integer>();
    for (var link : links) {
      if (link instanceof PendingDefinitionReference pending
          && indices.add(pending.declarationIndex())
          && pending.declarationIndex() != indices.size() - 1)
        throw new IllegalArgumentException("content slots must be dense in first-use order");
    }
    slots = indices.size();
    var writer = new CanonicalWriter().writeTag("core-definition-content").writeInt(slots);
    writer.writeBytes(CoreCodec.encodeDefinitionContent(definition));
    canonicalBytes = writer.toByteArray();
    id =
        new CoreContentId(
            ContentHasher.hash(
                "norm:core:content:v1\0", CoreIdentityVersion.CURRENT, canonicalBytes));
  }

  public CoreContentId id() {
    return id;
  }

  public CoreDefinition definition() {
    return definition;
  }

  public int slots() {
    return slots;
  }

  CoreDefinition bind(CoreLinkage linkage) {
    if (linkage.declarations().size() != slots)
      throw new IllegalArgumentException("linkage must bind every content slot exactly once");
    if (slots == 0) return definition;
    return CoreRewriter.resolve(
        definition,
        pending ->
            new PendingDefinitionReference(linkage.declarations().get(pending.declarationIndex())));
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof CoreDefinitionContent content
            && id.equals(content.id)
            && Arrays.equals(canonicalBytes, content.canonicalBytes);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
