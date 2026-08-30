package dev.w0fv1.norm.core.store;

import java.util.List;

public record PutBatchResult(List<PutResult> results) {
  public PutBatchResult {
    results = List.copyOf(results);
  }
}
