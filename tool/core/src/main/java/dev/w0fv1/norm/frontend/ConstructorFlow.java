package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SymbolId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

record ConstructorFlow(
    Optional<Set<SymbolId>> normal,
    Optional<Set<SymbolId>> returned,
    Optional<Set<SymbolId>> broken,
    Optional<Set<SymbolId>> continued,
    Optional<Set<SymbolId>> thrown) {
  ConstructorFlow {
    normal = immutable(normal);
    returned = immutable(returned);
    broken = immutable(broken);
    continued = immutable(continued);
    thrown = immutable(thrown);
  }

  static ConstructorFlow empty() {
    return new ConstructorFlow(
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
  }

  static ConstructorFlow normal(Set<SymbolId> assigned) {
    return empty().withNormal(assigned);
  }

  static ConstructorFlow returned(Set<SymbolId> assigned) {
    return empty().withReturned(assigned);
  }

  static ConstructorFlow broken(Set<SymbolId> assigned) {
    return empty().withBroken(assigned);
  }

  static ConstructorFlow continued(Set<SymbolId> assigned) {
    return empty().withContinued(assigned);
  }

  static ConstructorFlow thrown(Set<SymbolId> assigned) {
    return empty().withThrown(assigned);
  }

  ConstructorFlow then(ConstructorFlow next) {
    return new ConstructorFlow(
        next.normal,
        mergeAssigned(returned, next.returned),
        mergeAssigned(broken, next.broken),
        mergeAssigned(continued, next.continued),
        mergeAssigned(thrown, next.thrown));
  }

  ConstructorFlow merge(ConstructorFlow other) {
    return new ConstructorFlow(
        mergeAssigned(normal, other.normal),
        mergeAssigned(returned, other.returned),
        mergeAssigned(broken, other.broken),
        mergeAssigned(continued, other.continued),
        mergeAssigned(thrown, other.thrown));
  }

  List<Set<SymbolId>> completionStates() {
    List<Set<SymbolId>> states = new ArrayList<>(5);
    normal.ifPresent(states::add);
    returned.ifPresent(states::add);
    broken.ifPresent(states::add);
    continued.ifPresent(states::add);
    thrown.ifPresent(states::add);
    return List.copyOf(states);
  }

  ConstructorFlow afterFinally(Optional<Set<SymbolId>> after) {
    if (after.isEmpty()) return empty();
    Set<SymbolId> assigned = after.orElseThrow();
    return new ConstructorFlow(
        applyWrites(normal, assigned),
        applyWrites(returned, assigned),
        applyWrites(broken, assigned),
        applyWrites(continued, assigned),
        applyWrites(thrown, assigned));
  }

  ConstructorFlow withoutNormal() {
    return new ConstructorFlow(Optional.empty(), returned, broken, continued, thrown);
  }

  ConstructorFlow withoutNormalAndBroken() {
    return new ConstructorFlow(Optional.empty(), returned, Optional.empty(), continued, thrown);
  }

  ConstructorFlow withNormal(Set<SymbolId> assigned) {
    return new ConstructorFlow(Optional.of(assigned), returned, broken, continued, thrown);
  }

  ConstructorFlow withReturned(Set<SymbolId> assigned) {
    return new ConstructorFlow(normal, Optional.of(assigned), broken, continued, thrown);
  }

  ConstructorFlow withBroken(Set<SymbolId> assigned) {
    return new ConstructorFlow(normal, returned, Optional.of(assigned), continued, thrown);
  }

  ConstructorFlow withContinued(Set<SymbolId> assigned) {
    return new ConstructorFlow(normal, returned, broken, Optional.of(assigned), thrown);
  }

  ConstructorFlow withThrown(Set<SymbolId> assigned) {
    return new ConstructorFlow(normal, returned, broken, continued, Optional.of(assigned));
  }

  static Set<SymbolId> intersect(List<Set<SymbolId>> values) {
    Set<SymbolId> result = new HashSet<>(values.getFirst());
    values.forEach(result::retainAll);
    return Set.copyOf(result);
  }

  static Optional<Set<SymbolId>> mergeAssigned(
      Optional<Set<SymbolId>> left, Optional<Set<SymbolId>> right) {
    if (left.isEmpty()) return right;
    if (right.isEmpty()) return left;
    return Optional.of(intersect(List.of(left.orElseThrow(), right.orElseThrow())));
  }

  private static Optional<Set<SymbolId>> immutable(Optional<Set<SymbolId>> assigned) {
    return assigned.map(Set::copyOf);
  }

  private static Optional<Set<SymbolId>> applyWrites(
      Optional<Set<SymbolId>> completion, Set<SymbolId> writes) {
    return completion.map(
        assigned -> {
          Set<SymbolId> result = new HashSet<>(assigned);
          result.addAll(writes);
          return Set.copyOf(result);
        });
  }
}
