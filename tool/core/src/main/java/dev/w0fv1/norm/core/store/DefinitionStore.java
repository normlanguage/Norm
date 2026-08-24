package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.core.DefinitionGroupId;
import java.io.IOException;
import java.util.Optional;

public interface DefinitionStore {
  PutResult put(byte[] canonicalGroup) throws IOException;

  Optional<byte[]> get(DefinitionGroupId id) throws IOException;
}
