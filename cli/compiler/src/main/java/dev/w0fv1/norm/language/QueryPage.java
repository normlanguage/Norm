package dev.w0fv1.norm.language;

import java.util.List;

public record QueryPage<T>(List<T> items, int offset, int total) {
  public QueryPage {
    items = List.copyOf(items);
    if (offset < 0 || total < 0 || items.size() > Math.max(0, total - offset))
      throw new IllegalArgumentException("invalid query page");
  }

  public static <T> QueryPage<T> of(List<T> values, int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 1000)
      throw new IllegalArgumentException(
          "offset must be nonnegative and limit must be between 1 and 1000");
    int start = Math.min(offset, values.size());
    return new QueryPage<>(
        values.subList(start, start + Math.min(limit, values.size() - start)),
        offset,
        values.size());
  }

  public boolean hasMore() {
    return (long) offset + items.size() < total;
  }
}
