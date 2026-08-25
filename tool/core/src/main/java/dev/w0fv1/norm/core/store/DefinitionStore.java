package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.core.DefinitionGroupId;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface DefinitionStore {
  PutBatchResult putAll(List<byte[]> canonicalGroups) throws IOException;

  Optional<byte[]> get(DefinitionGroupId id) throws IOException;
}
