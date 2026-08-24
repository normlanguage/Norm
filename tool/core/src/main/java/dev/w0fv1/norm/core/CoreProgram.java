package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class CoreProgram {
  private final List<CoreDefinitionGroup> groups;
  private final Map<DefinitionGroupId, CoreDefinitionGroup> groupsById;

  public CoreProgram(List<CoreDefinitionGroup> groups) {
    this.groups = groups.stream().sorted((left, right) -> left.id().compareTo(right.id())).toList();
    Map<DefinitionGroupId, CoreDefinitionGroup> indexed = new LinkedHashMap<>();
    for (CoreDefinitionGroup group : this.groups) {
      if (indexed.putIfAbsent(group.id(), group) != null) {
        throw new IllegalArgumentException("duplicate definition group id: " + group.id());
      }
    }
    groupsById = Map.copyOf(indexed);
    for (CoreDefinitionRecord record : definitions()) {
      for (CoreDefinitionLink link : CoreTree.links(record.definition())) {
        if (!(link instanceof DefinitionReference reference)) {
          throw new IllegalArgumentException("core program contains a pending reference");
        }
        DefinitionId target = resolve(record.id(), reference);
        if (definition(target).isEmpty()) {
          throw new IllegalArgumentException("core program dependency is absent: " + target);
        }
      }
    }
    CoreProgramVerifier.verify(this);
  }

  public List<CoreDefinitionGroup> groups() {
    return groups;
  }

  public Optional<CoreDefinitionGroup> group(DefinitionGroupId id) {
    return Optional.ofNullable(groupsById.get(Objects.requireNonNull(id, "id")));
  }

  public Optional<CoreDefinition> definition(DefinitionId id) {
    Objects.requireNonNull(id, "id");
    CoreDefinitionGroup group = groupsById.get(id.group());
    if (group == null || id.memberIndex() >= group.definitions().size()) return Optional.empty();
    return Optional.of(group.definitions().get(id.memberIndex()));
  }

  public List<CoreDefinition.Callable> callables() {
    return definitions(CoreDefinition.Callable.class);
  }

  public List<CoreDefinitionRecord> definitions() {
    List<CoreDefinitionRecord> result = new ArrayList<>();
    for (CoreDefinitionGroup group : groups) {
      for (int member = 0; member < group.definitions().size(); member++) {
        result.add(
            new CoreDefinitionRecord(group.definitionId(member), group.definitions().get(member)));
      }
    }
    return List.copyOf(result);
  }

  public List<CoreDefinition.Class> classes() {
    return definitions(CoreDefinition.Class.class);
  }

  public List<CoreDefinition.Enum> enums() {
    return definitions(CoreDefinition.Enum.class);
  }

  public DefinitionId resolve(DefinitionId owner, DefinitionReference reference) {
    Objects.requireNonNull(owner, "owner");
    return switch (Objects.requireNonNull(reference, "reference")) {
      case DefinitionReference.External external -> external.definition();
      case DefinitionReference.RecursiveMember recursive -> {
        CoreDefinitionGroup group = groupsById.get(owner.group());
        if (group == null || recursive.memberIndex() >= group.definitions().size()) {
          throw new IllegalArgumentException("recursive definition reference is outside its group");
        }
        yield new DefinitionId(owner.group(), recursive.memberIndex());
      }
    };
  }

  private <T extends CoreDefinition> List<T> definitions(java.lang.Class<T> type) {
    List<T> result = new ArrayList<>();
    groups.forEach(
        group ->
            group.definitions().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .forEach(result::add));
    return List.copyOf(result);
  }
}
