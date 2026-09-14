package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CoreCompilationInput(List<Declaration> declarations, CoreProgram dependencies) {
  public CoreCompilationInput {
    declarations = List.copyOf(declarations);
    Objects.requireNonNull(dependencies, "dependencies");
    for (var declaration : declarations) {
      if (declaration instanceof Compiled compiled)
        for (var member : compiled.equivalentDefinitions())
          if (dependencies.definition(member).isEmpty())
            throw new IllegalArgumentException("compiled definition is absent: " + member);
    }
  }

  public static CoreCompilationInput source(List<CoreDefinition> definitions) {
    return new CoreCompilationInput(
        definitions.stream().map(Source::new).map(Declaration.class::cast).toList(),
        new CoreProgram(List.of()));
  }

  public sealed interface Declaration permits Source, Compiled {}

  public record Source(CoreDefinition definition) implements Declaration {
    public Source {
      Objects.requireNonNull(definition, "definition");
    }

    public Set<Integer> references() {
      return CoreTree.links(definition).stream()
          .filter(PendingDefinitionReference.class::isInstance)
          .map(PendingDefinitionReference.class::cast)
          .map(PendingDefinitionReference::declarationIndex)
          .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Source relocate(java.util.function.IntUnaryOperator indices) {
      return new Source(
          CoreRewriter.resolve(
              definition,
              pending ->
                  new PendingDefinitionReference(indices.applyAsInt(pending.declarationIndex()))));
    }
  }

  public record Compiled(DefinitionId definition, Set<DefinitionId> equivalentDefinitions)
      implements Declaration {
    public Compiled {
      Objects.requireNonNull(definition, "definition");
      equivalentDefinitions = Set.copyOf(equivalentDefinitions);
      if (!equivalentDefinitions.contains(definition)
          || equivalentDefinitions.stream()
              .anyMatch(member -> !member.group().equals(definition.group())))
        throw new IllegalArgumentException(
            "compiled equivalents must contain the definition and belong to its group");
    }
  }
}
