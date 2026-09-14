package dev.w0fv1.norm.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CoreCanonicalizer {
  public CoreCanonicalizer() {}

  public Result canonicalize(List<CoreDefinition> pendingDefinitions) {
    return canonicalize(pendingDefinitions, CoreCanonicalizationControl.standard());
  }

  public Result canonicalize(
      List<CoreDefinition> pendingDefinitions, CoreCanonicalizationControl control) {
    return canonicalize(CoreCompilationInput.source(pendingDefinitions), control);
  }

  public Result canonicalize(CoreCompilationInput input) {
    return canonicalize(input, CoreCanonicalizationControl.standard());
  }

  public Result canonicalize(CoreCompilationInput input, CoreCanonicalizationControl control) {
    Objects.requireNonNull(input, "input");
    CoreCanonicalizationControl.State state = Objects.requireNonNull(control, "control").begin();
    List<CoreDefinition> definitions = new ArrayList<>(input.declarations().size());
    Set<Integer> compiled = new java.util.HashSet<>();
    Map<Integer, DefinitionId> definitionIds = new LinkedHashMap<>();
    Map<Integer, Set<DefinitionId>> definitionOrbits = new LinkedHashMap<>();
    Map<DefinitionGroupId, CoreDefinitionGroup> groups = new LinkedHashMap<>();
    for (int index = 0; index < input.declarations().size(); index++) {
      state.checkpoint();
      switch (input.declarations().get(index)) {
        case CoreCompilationInput.Source source -> definitions.add(source.definition());
        case CoreCompilationInput.Compiled reused -> {
          definitions.add(input.dependencies().definition(reused.definition()).orElseThrow());
          compiled.add(index);
          definitionIds.put(index, reused.definition());
          definitionOrbits.put(index, reused.equivalentDefinitions());
          groups.putIfAbsent(
              reused.definition().group(),
              input.dependencies().group(reused.definition().group()).orElseThrow());
        }
      }
    }
    List<List<Integer>> components = stronglyConnectedComponents(definitions, compiled, state);
    for (List<Integer> component : components) {
      state.checkpoint();
      CanonicalGroup canonical = canonicalizeGroup(component, definitions, definitionIds, state);
      groups.putIfAbsent(canonical.group().id(), canonical.group());
      canonical
          .memberOrbits()
          .forEach(
              (declaration, memberIndices) -> {
                Set<DefinitionId> orbit =
                    memberIndices.stream()
                        .map(canonical.group()::definitionId)
                        .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
                definitionOrbits.put(declaration, Set.copyOf(orbit));
                definitionIds.put(declaration, orbit.iterator().next());
              });
    }
    var required =
        new ArrayDeque<>(
            input.dependencies().groups().isEmpty()
                ? List.<CoreDefinitionGroup>of()
                : groups.values());
    while (!required.isEmpty()) {
      state.checkpoint();
      for (var definition : required.removeFirst().definitions()) {
        for (var link : CoreTree.links(definition)) {
          if (link instanceof DefinitionReference.External external
              && !groups.containsKey(external.definition().group())) {
            input
                .dependencies()
                .group(external.definition().group())
                .ifPresent(
                    group -> {
                      groups.put(group.id(), group);
                      required.addLast(group);
                    });
          }
        }
      }
    }
    int maximumComponentSize = components.stream().mapToInt(List::size).max().orElse(0);
    return new Result(
        List.copyOf(groups.values()),
        Map.copyOf(definitionIds),
        Map.copyOf(definitionOrbits),
        state.metrics(components.size(), maximumComponentSize));
  }

  private static CanonicalGroup canonicalizeGroup(
      List<Integer> component,
      List<CoreDefinition> definitions,
      Map<Integer, DefinitionId> definitionIds,
      CoreCanonicalizationControl.State state) {
    if (component.size() == 1)
      return new CanonicalGroup(
          CoreDefinitionGroup.create(resolve(component, definitions, definitionIds)),
          Map.of(component.getFirst(), Set.of(0)));
    CanonicalLabeling labeling = canonicalLabeling(component, definitions, definitionIds, state);
    List<Integer> order = labeling.order();
    return new CanonicalGroup(
        CoreDefinitionGroup.create(resolve(order, definitions, definitionIds)),
        labeling.memberOrbits());
  }

  private static List<CoreDefinition> resolve(
      List<Integer> order,
      List<CoreDefinition> definitions,
      Map<Integer, DefinitionId> definitionIds) {
    Map<Integer, Integer> memberIndices = new HashMap<>();
    for (int index = 0; index < order.size(); index++) memberIndices.put(order.get(index), index);
    return order.stream()
        .map(
            declaration ->
                CoreTree.resolve(
                    definitions.get(declaration),
                    pending -> {
                      Integer member = memberIndices.get(pending.declarationIndex());
                      if (member != null) {
                        return new DefinitionReference.RecursiveMember(member);
                      }
                      DefinitionId external = definitionIds.get(pending.declarationIndex());
                      if (external == null) {
                        throw new IllegalStateException("external definition is unresolved");
                      }
                      return new DefinitionReference.External(external);
                    }))
        .toList();
  }

  private static CanonicalLabeling canonicalLabeling(
      List<Integer> component,
      List<CoreDefinition> definitions,
      Map<Integer, DefinitionId> definitionIds,
      CoreCanonicalizationControl.State state) {
    Set<Integer> members = Set.copyOf(component);
    Map<Integer, byte[]> labels = new HashMap<>();
    for (int declaration : component) {
      labels.put(
          declaration,
          CoreCodec.encodeDefinition(
              definitions.get(declaration),
              link -> shapeReference(link, members, definitionIds, Map.of())));
    }
    Map<Integer, Integer> partition =
        refine(component, definitions, definitionIds, colors(labels), state);
    return search(component, definitions, definitionIds, partition, state, new HashMap<>());
  }

  private static CanonicalLabeling search(
      List<Integer> component,
      List<CoreDefinition> definitions,
      Map<Integer, DefinitionId> definitionIds,
      Map<Integer, Integer> partition,
      CoreCanonicalizationControl.State state,
      Map<List<Integer>, CanonicalLabeling> memoized) {
    state.checkpoint();
    List<Integer> stateKey = component.stream().map(partition::get).toList();
    CanonicalLabeling memoizedResult = memoized.get(stateKey);
    if (memoizedResult != null) {
      state.memoizedSearch();
      return memoizedResult;
    }
    List<Integer> cell = selectedCell(component, partition);
    if (cell.isEmpty()) {
      List<Integer> order =
          component.stream().sorted(Comparator.comparingInt(partition::get)).toList();
      Map<Integer, Integer> memberIndices = new HashMap<>();
      for (int index = 0; index < order.size(); index++) memberIndices.put(order.get(index), index);
      Map<Integer, Set<Integer>> memberOrbits = new HashMap<>();
      memberIndices.forEach(
          (declaration, memberIndex) -> memberOrbits.put(declaration, Set.of(memberIndex)));
      CanonicalLabeling result =
          new CanonicalLabeling(
              order,
              CoreCodec.encodeGroup(resolve(order, definitions, definitionIds)),
              memberOrbits);
      memoized.put(stateKey, result);
      return result;
    }
    CanonicalLabeling best = null;
    List<SearchedBranch> searched = new ArrayList<>();
    for (int candidate : cell) {
      SearchedBranch equivalent =
          searched.stream()
              .filter(
                  branch ->
                      isTranspositionAutomorphism(
                          component, definitions, definitionIds, branch.candidate(), candidate))
              .findFirst()
              .orElse(null);
      CanonicalLabeling current;
      if (equivalent != null) {
        state.automorphicBranch();
        current = equivalent.labeling().transpose(equivalent.candidate(), candidate);
        searched.add(new SearchedBranch(candidate, current));
      } else {
        state.searchBranch();
        Map<Integer, byte[]> individualized = new HashMap<>();
        for (int declaration : component) {
          individualized.put(
              declaration,
              new CanonicalWriter()
                  .writeTag("canonical-partition")
                  .writeInt(partition.get(declaration))
                  .writeBoolean(declaration == candidate)
                  .toByteArray());
        }
        Map<Integer, Integer> refined =
            refine(component, definitions, definitionIds, colors(individualized), state);
        current = search(component, definitions, definitionIds, refined, state, memoized);
        searched.add(new SearchedBranch(candidate, current));
      }
      if (best == null
          || Arrays.compareUnsigned(current.canonicalBytes(), best.canonicalBytes()) < 0) {
        best = current;
      } else if (Arrays.compareUnsigned(current.canonicalBytes(), best.canonicalBytes()) == 0) {
        best = best.mergeMemberOrbits(current);
      }
    }
    CanonicalLabeling result = Objects.requireNonNull(best, "canonical labeling");
    memoized.put(stateKey, result);
    return result;
  }

  private static boolean isTranspositionAutomorphism(
      List<Integer> component,
      List<CoreDefinition> definitions,
      Map<Integer, DefinitionId> definitionIds,
      int first,
      int second) {
    Set<Integer> members = Set.copyOf(component);
    for (int declaration : component) {
      byte[] transformed =
          CoreCodec.encodeDefinition(
              definitions.get(declaration),
              link -> permutedReference(link, members, definitionIds, first, second));
      byte[] target =
          CoreCodec.encodeDefinition(
              definitions.get(transpose(declaration, first, second)),
              link -> permutedReference(link, members, definitionIds, -1, -1));
      if (!Arrays.equals(transformed, target)) return false;
    }
    return true;
  }

  private static DefinitionReference permutedReference(
      CoreDefinitionLink link,
      Set<Integer> members,
      Map<Integer, DefinitionId> definitionIds,
      int first,
      int second) {
    if (link instanceof DefinitionReference reference) return reference;
    int target = ((PendingDefinitionReference) link).declarationIndex();
    if (members.contains(target)) {
      return new DefinitionReference.RecursiveMember(transpose(target, first, second));
    }
    DefinitionId external = definitionIds.get(target);
    if (external == null) throw new IllegalStateException("external definition is unresolved");
    return new DefinitionReference.External(external);
  }

  private static int transpose(int declaration, int first, int second) {
    if (declaration == first) return second;
    return declaration == second ? first : declaration;
  }

  private static Map<Integer, Integer> refine(
      List<Integer> component,
      List<CoreDefinition> definitions,
      Map<Integer, DefinitionId> definitionIds,
      Map<Integer, Integer> initial,
      CoreCanonicalizationControl.State state) {
    Set<Integer> members = Set.copyOf(component);
    Map<Integer, Integer> partition = initial;
    while (true) {
      state.refinementRound();
      Map<Integer, Integer> currentPartition = partition;
      Map<Integer, byte[]> labels = new HashMap<>();
      for (int declaration : component) {
        CanonicalWriter label =
            new CanonicalWriter()
                .writeTag("canonical-refinement")
                .writeInt(currentPartition.get(declaration))
                .writeBytes(
                    CoreCodec.encodeDefinition(
                        definitions.get(declaration),
                        link -> shapeReference(link, members, definitionIds, currentPartition)));
        List<byte[]> incoming =
            incomingLabels(
                declaration, component, definitions, definitionIds, currentPartition, members);
        label.writeInt(incoming.size());
        incoming.forEach(label::writeBytes);
        labels.put(declaration, label.toByteArray());
      }
      Map<Integer, Integer> refined = colors(labels);
      if (samePartition(component, partition, refined)) return refined;
      partition = refined;
    }
  }

  private static List<byte[]> incomingLabels(
      int target,
      List<Integer> component,
      List<CoreDefinition> definitions,
      Map<Integer, DefinitionId> definitionIds,
      Map<Integer, Integer> partition,
      Set<Integer> members) {
    int distinguishedColor =
        partition.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    List<byte[]> incoming = new ArrayList<>();
    for (int source : component) {
      boolean referencesTarget =
          CoreTree.links(definitions.get(source)).stream()
              .filter(PendingDefinitionReference.class::isInstance)
              .map(PendingDefinitionReference.class::cast)
              .anyMatch(link -> link.declarationIndex() == target);
      if (!referencesTarget) continue;
      incoming.add(
          new CanonicalWriter()
              .writeTag("canonical-incoming")
              .writeInt(partition.get(source))
              .writeBytes(
                  CoreCodec.encodeDefinition(
                      definitions.get(source),
                      link -> {
                        if (link instanceof DefinitionReference reference) return reference;
                        int linked = ((PendingDefinitionReference) link).declarationIndex();
                        if (members.contains(linked)) {
                          return new DefinitionReference.RecursiveMember(
                              linked == target ? distinguishedColor : partition.get(linked));
                        }
                        DefinitionId external = definitionIds.get(linked);
                        if (external == null) {
                          throw new IllegalStateException("external definition is unresolved");
                        }
                        return new DefinitionReference.External(external);
                      }))
              .toByteArray());
    }
    incoming.sort(Arrays::compareUnsigned);
    return List.copyOf(incoming);
  }

  private static boolean samePartition(
      List<Integer> component, Map<Integer, Integer> left, Map<Integer, Integer> right) {
    for (int first : component) {
      for (int second : component) {
        if ((left.get(first).equals(left.get(second)))
            != (right.get(first).equals(right.get(second)))) {
          return false;
        }
      }
    }
    return true;
  }

  private static List<Integer> selectedCell(
      List<Integer> component, Map<Integer, Integer> partition) {
    Map<Integer, List<Integer>> cells = new HashMap<>();
    for (int declaration : component) {
      cells
          .computeIfAbsent(partition.get(declaration), ignored -> new ArrayList<>())
          .add(declaration);
    }
    return cells.entrySet().stream()
        .filter(entry -> entry.getValue().size() > 1)
        .min(
            Comparator.<Map.Entry<Integer, List<Integer>>>comparingInt(
                    entry -> entry.getValue().size())
                .thenComparingInt(Map.Entry::getKey))
        .map(Map.Entry::getValue)
        .orElse(List.of());
  }

  private static DefinitionReference shapeReference(
      CoreDefinitionLink link,
      Set<Integer> members,
      Map<Integer, DefinitionId> definitionIds,
      Map<Integer, Integer> colors) {
    if (link instanceof DefinitionReference reference) return reference;
    int target = ((PendingDefinitionReference) link).declarationIndex();
    if (members.contains(target)) {
      return new DefinitionReference.RecursiveMember(colors.getOrDefault(target, 0));
    }
    DefinitionId external = definitionIds.get(target);
    if (external == null) throw new IllegalStateException("external definition is unresolved");
    return new DefinitionReference.External(external);
  }

  private static Map<Integer, Integer> colors(Map<Integer, byte[]> labels) {
    List<byte[]> distinct = new ArrayList<>();
    labels.values().stream()
        .sorted(Arrays::compareUnsigned)
        .forEach(
            label -> {
              if (distinct.isEmpty() || Arrays.compareUnsigned(distinct.getLast(), label) != 0) {
                distinct.add(label);
              }
            });
    Map<Integer, Integer> colors = new HashMap<>();
    labels.forEach(
        (declaration, label) -> {
          for (int index = 0; index < distinct.size(); index++) {
            if (Arrays.compareUnsigned(distinct.get(index), label) == 0) {
              colors.put(declaration, index);
              return;
            }
          }
          throw new IllegalStateException("definition color is absent");
        });
    return colors;
  }

  private static List<List<Integer>> stronglyConnectedComponents(
      List<CoreDefinition> definitions,
      Set<Integer> compiled,
      CoreCanonicalizationControl.State state) {
    List<int[]> dependencies = new ArrayList<>(definitions.size());
    for (int index = 0; index < definitions.size(); index++) {
      state.checkpoint();
      if (compiled.contains(index)) {
        dependencies.add(new int[0]);
        continue;
      }
      var definition = definitions.get(index);
      int[] targets =
          CoreTree.links(definition).stream()
              .filter(PendingDefinitionReference.class::isInstance)
              .map(PendingDefinitionReference.class::cast)
              .mapToInt(PendingDefinitionReference::declarationIndex)
              .filter(target -> !compiled.contains(target))
              .distinct()
              .sorted()
              .toArray();
      if (targets.length > 0 && targets[targets.length - 1] >= definitions.size())
        throw new IllegalArgumentException("pending reference is outside the definition set");
      dependencies.add(targets);
    }
    Tarjan tarjan = new Tarjan(dependencies, state);
    for (int declaration = 0; declaration < definitions.size(); declaration++) {
      if (!compiled.contains(declaration) && tarjan.indices[declaration] == -1)
        tarjan.visit(declaration);
    }
    return List.copyOf(tarjan.components);
  }

  public record Result(
      List<CoreDefinitionGroup> groups,
      Map<Integer, DefinitionId> definitionIds,
      Map<Integer, Set<DefinitionId>> definitionOrbits,
      CoreCanonicalizationMetrics metrics) {
    public Result {
      groups = List.copyOf(groups);
      definitionIds = Map.copyOf(definitionIds);
      Map<Integer, Set<DefinitionId>> stableOrbits = new HashMap<>();
      definitionOrbits.forEach(
          (declaration, orbit) -> stableOrbits.put(declaration, Set.copyOf(orbit)));
      definitionOrbits = Map.copyOf(stableOrbits);
      Objects.requireNonNull(metrics, "metrics");
    }
  }

  private record CanonicalGroup(
      CoreDefinitionGroup group, Map<Integer, Set<Integer>> memberOrbits) {
    private CanonicalGroup {
      Objects.requireNonNull(group, "group");
      Map<Integer, Set<Integer>> stableOrbits = new HashMap<>();
      memberOrbits.forEach(
          (declaration, orbit) -> stableOrbits.put(declaration, Set.copyOf(orbit)));
      memberOrbits = Map.copyOf(stableOrbits);
    }
  }

  private record CanonicalLabeling(
      List<Integer> order, byte[] canonicalBytes, Map<Integer, Set<Integer>> memberOrbits) {
    private CanonicalLabeling {
      order = List.copyOf(order);
      canonicalBytes = canonicalBytes.clone();
      Map<Integer, Set<Integer>> stableOrbits = new HashMap<>();
      memberOrbits.forEach(
          (declaration, orbit) -> stableOrbits.put(declaration, Set.copyOf(orbit)));
      memberOrbits = Map.copyOf(stableOrbits);
    }

    @Override
    public byte[] canonicalBytes() {
      return canonicalBytes.clone();
    }

    private CanonicalLabeling mergeMemberOrbits(CanonicalLabeling other) {
      Map<Integer, Set<Integer>> merged = new HashMap<>();
      memberOrbits.forEach(
          (declaration, orbit) -> merged.put(declaration, new LinkedHashSet<>(orbit)));
      other.memberOrbits.forEach(
          (declaration, orbit) ->
              merged.computeIfAbsent(declaration, ignored -> new LinkedHashSet<>()).addAll(orbit));
      return new CanonicalLabeling(order, canonicalBytes, merged);
    }

    private CanonicalLabeling transpose(int first, int second) {
      List<Integer> transposedOrder =
          order.stream()
              .map(declaration -> CoreCanonicalizer.transpose(declaration, first, second))
              .toList();
      Map<Integer, Set<Integer>> transposedOrbits = new HashMap<>();
      memberOrbits.forEach(
          (declaration, orbit) ->
              transposedOrbits.put(CoreCanonicalizer.transpose(declaration, first, second), orbit));
      return new CanonicalLabeling(transposedOrder, canonicalBytes, transposedOrbits);
    }
  }

  private record SearchedBranch(int candidate, CanonicalLabeling labeling) {}

  private static final class Tarjan {
    private final List<int[]> dependencies;
    private final CoreCanonicalizationControl.State state;
    private final int[] indices;
    private final int[] lowLinks;
    private final int[] cursors;
    private final boolean[] onStack;
    private final ArrayDeque<Integer> stack = new ArrayDeque<>();
    private final ArrayDeque<Integer> traversal = new ArrayDeque<>();
    private final List<List<Integer>> components = new ArrayList<>();
    private int nextIndex;

    private Tarjan(List<int[]> dependencies, CoreCanonicalizationControl.State state) {
      this.dependencies = dependencies;
      this.state = state;
      indices = new int[dependencies.size()];
      Arrays.fill(indices, -1);
      lowLinks = new int[dependencies.size()];
      cursors = new int[dependencies.size()];
      onStack = new boolean[dependencies.size()];
    }

    private void enter(int declaration) {
      indices[declaration] = nextIndex;
      lowLinks[declaration] = nextIndex++;
      stack.push(declaration);
      onStack[declaration] = true;
      traversal.push(declaration);
    }

    private void visit(int root) {
      enter(root);
      while (!traversal.isEmpty()) {
        state.checkpoint();
        int declaration = traversal.peek();
        int[] targets = dependencies.get(declaration);
        if (cursors[declaration] < targets.length) {
          int dependency = targets[cursors[declaration]++];
          if (indices[dependency] == -1) enter(dependency);
          else if (onStack[dependency])
            lowLinks[declaration] = Math.min(lowLinks[declaration], indices[dependency]);
          continue;
        }
        traversal.pop();
        if (!traversal.isEmpty()) {
          int parent = traversal.peek();
          lowLinks[parent] = Math.min(lowLinks[parent], lowLinks[declaration]);
        }
        if (lowLinks[declaration] != indices[declaration]) continue;
        List<Integer> component = new ArrayList<>();
        int member;
        do {
          member = stack.pop();
          onStack[member] = false;
          component.add(member);
        } while (member != declaration);
        components.add(List.copyOf(component));
      }
    }
  }
}
