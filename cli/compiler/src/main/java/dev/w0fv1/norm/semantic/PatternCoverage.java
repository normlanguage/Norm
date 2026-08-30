package dev.w0fv1.norm.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PatternCoverage<T> {
  private final Domain<T> domain;

  public PatternCoverage(Domain<T> domain) {
    this.domain = Objects.requireNonNull(domain, "domain");
  }

  public boolean isUseful(List<Pattern> previous, Pattern candidate, T type) {
    List<List<Pattern>> matrix = previous.stream().map(List::of).toList();
    return useful(matrix, List.of(candidate), List.of(type));
  }

  public boolean isExhaustive(List<Pattern> patterns, T type) {
    return !isUseful(patterns, Pattern.any(), type);
  }

  private boolean useful(List<List<Pattern>> matrix, List<Pattern> vector, List<T> types) {
    if (vector.isEmpty()) return matrix.isEmpty();
    Pattern head = vector.getFirst();
    List<Pattern> tail = vector.subList(1, vector.size());
    List<T> typeTail = types.subList(1, types.size());
    if (head instanceof ConstructorPattern constructor) {
      Constructor<T> shape = constructor(type(types), constructor.key());
      if (shape == null || shape.argumentTypes().size() != constructor.arguments().size()) {
        return false;
      }
      return useful(
          specialize(matrix, shape),
          concat(constructor.arguments(), tail),
          concat(shape.argumentTypes(), typeTail));
    }
    List<Constructor<T>> constructors = domain.constructors(type(types));
    if (!constructors.isEmpty() && complete(matrix, constructors)) {
      for (Constructor<T> constructor : constructors) {
        if (useful(
            specialize(matrix, constructor),
            concat(any(constructor.argumentTypes().size()), tail),
            concat(constructor.argumentTypes(), typeTail))) {
          return true;
        }
      }
      return false;
    }
    return useful(defaultMatrix(matrix), tail, typeTail);
  }

  private T type(List<T> types) {
    if (types.isEmpty()) throw new IllegalArgumentException("pattern type vector is empty");
    return types.getFirst();
  }

  private Constructor<T> constructor(T type, String key) {
    return domain.constructors(type).stream()
        .filter(candidate -> candidate.key().equals(key))
        .findFirst()
        .orElseGet(() -> domain.openConstructor(type, key));
  }

  private static <T> boolean complete(
      List<List<Pattern>> matrix, List<Constructor<T>> constructors) {
    java.util.Set<String> present = new java.util.HashSet<>();
    for (List<Pattern> row : matrix) {
      if (!row.isEmpty() && row.getFirst() instanceof ConstructorPattern constructor) {
        present.add(constructor.key());
      }
    }
    return constructors.stream().allMatch(constructor -> present.contains(constructor.key()));
  }

  private static <T> List<List<Pattern>> specialize(
      List<List<Pattern>> matrix, Constructor<T> constructor) {
    List<List<Pattern>> result = new ArrayList<>();
    for (List<Pattern> row : matrix) {
      if (row.isEmpty()) continue;
      Pattern head = row.getFirst();
      List<Pattern> tail = row.subList(1, row.size());
      if (head instanceof AnyPattern) {
        result.add(concat(any(constructor.argumentTypes().size()), tail));
      } else if (head instanceof ConstructorPattern pattern
          && pattern.key().equals(constructor.key())) {
        result.add(concat(pattern.arguments(), tail));
      }
    }
    return result;
  }

  private static List<List<Pattern>> defaultMatrix(List<List<Pattern>> matrix) {
    return matrix.stream()
        .filter(row -> !row.isEmpty() && row.getFirst() instanceof AnyPattern)
        .map(row -> List.copyOf(row.subList(1, row.size())))
        .toList();
  }

  private static List<Pattern> any(int count) {
    List<Pattern> result = new ArrayList<>();
    for (int index = 0; index < count; index++) result.add(Pattern.any());
    return List.copyOf(result);
  }

  private static <V> List<V> concat(List<V> first, List<V> second) {
    List<V> result = new ArrayList<>(first.size() + second.size());
    result.addAll(first);
    result.addAll(second);
    return List.copyOf(result);
  }

  public interface Domain<T> {
    List<Constructor<T>> constructors(T type);

    Constructor<T> openConstructor(T type, String key);
  }

  public record Constructor<T>(String key, List<T> argumentTypes) {
    public Constructor {
      Objects.requireNonNull(key, "key");
      argumentTypes = List.copyOf(argumentTypes);
    }
  }

  public sealed interface Pattern permits AnyPattern, ConstructorPattern {
    static Pattern any() {
      return AnyPattern.INSTANCE;
    }

    static Pattern constructor(String key, List<Pattern> arguments) {
      return new ConstructorPattern(key, arguments);
    }
  }

  public enum AnyPattern implements Pattern {
    INSTANCE
  }

  public record ConstructorPattern(String key, List<Pattern> arguments) implements Pattern {
    public ConstructorPattern {
      Objects.requireNonNull(key, "key");
      arguments = List.copyOf(arguments);
    }
  }
}
