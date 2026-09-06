package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class CoreTree {
  private CoreTree() {}

  static List<CoreDefinitionLink> links(CoreDefinition definition) {
    return dependencies(definition).stream().map(CoreDependency::target).toList();
  }

  static List<CoreDependency> dependencies(CoreDefinition definition) {
    return dependencies(definition, CoreWalker::walk);
  }

  static List<CoreDependency> declarationDependencies(CoreDefinition definition) {
    return dependencies(definition, CoreWalker::walkDeclaration);
  }

  static List<CoreDependency> executionDependencies(CoreDefinition definition) {
    return dependencies(definition, CoreWalker::walkExecution);
  }

  private static List<CoreDependency> dependencies(
      CoreDefinition definition,
      java.util.function.BiConsumer<CoreWalker, CoreDefinition> traversal) {
    List<CoreDependency> result = new ArrayList<>();
    var walker =
        new CoreWalker() {
          @Override
          protected void visitDependency(CoreDependency.Kind kind, CoreDefinitionLink link) {
            result.add(new CoreDependency(kind, link));
          }
        };
    traversal.accept(walker, definition);
    return List.copyOf(result);
  }

  static Map<Integer, DefinitionReference> referenceSites(CoreDefinition definition) {
    Map<Integer, DefinitionReference> result = new LinkedHashMap<>();
    new CoreWalker() {
      @Override
      protected void visitReference(int nodeIndex, CoreDefinitionLink link) {
        if (!(link instanceof DefinitionReference reference)) {
          throw new IllegalArgumentException("core definition contains a pending reference");
        }
        if (result.putIfAbsent(nodeIndex, reference) != null) {
          throw new IllegalArgumentException("core reference node index is duplicated");
        }
      }
    }.walk(definition);
    return Map.copyOf(result);
  }

  static CoreDefinition resolve(
      CoreDefinition definition,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return CoreRewriter.resolve(definition, resolver);
  }
}
