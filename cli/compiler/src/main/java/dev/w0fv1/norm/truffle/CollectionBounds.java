package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.execution.RuntimeErrorCode;

final class CollectionBounds {
  private CollectionBounds() {}

  static int index(Object value, int size, Node location) {
    int index = (Integer) value;
    if (index < 0 || index >= size) {
      throw new NormGuestException(
          RuntimeErrorCode.INDEX_OUT_OF_BOUNDS,
          "index " + index + " is outside collection size " + size,
          location);
    }
    return index;
  }
}
