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

  public record Source(CoreDefinitionContent content, CoreLinkage linkage) implements Declaration {
    public Source {
      Objects.requireNonNull(content, "content");
      Objects.requireNonNull(linkage, "linkage");
      if (content.slots() != linkage.declarations().size())
        throw new IllegalArgumentException("linkage must bind every content slot exactly once");
    }

    public Source(CoreDefinition definition) {
      this(capture(definition));
    }

    private Source(Source captured) {
      this(captured.content(), captured.linkage());
    }

    private static Source capture(CoreDefinition definition) {
      Objects.requireNonNull(definition, "definition");
      var slots = new java.util.LinkedHashMap<Integer, Integer>();
      CoreTree.links(definition).stream()
          .filter(PendingDefinitionReference.class::isInstance)
          .map(PendingDefinitionReference.class::cast)
          .forEach(
              pending ->
                  slots.computeIfAbsent(pending.declarationIndex(), ignored -> slots.size()));
      var normalized =
          CoreRewriter.resolve(
              definition,
              pending -> new PendingDefinitionReference(slots.get(pending.declarationIndex())));
      return new Source(
          new CoreDefinitionContent(normalized), new CoreLinkage(List.copyOf(slots.keySet())));
    }

    public CoreDefinition definition() {
      return content.bind(linkage);
    }

    public Set<Integer> references() {
      return Set.copyOf(linkage.declarations());
    }

    public Source relocate(java.util.function.IntUnaryOperator indices) {
      return new Source(content, linkage.relocate(indices));
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
