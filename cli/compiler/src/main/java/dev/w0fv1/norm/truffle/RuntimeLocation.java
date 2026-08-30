package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.DefinitionOccurrenceId;

interface RuntimeLocation {
  DefinitionOccurrenceId occurrence();

  int nodeIndex();
}
