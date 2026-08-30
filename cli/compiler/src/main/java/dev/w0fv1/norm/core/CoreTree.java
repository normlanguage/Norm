package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class CoreTree {
  private CoreTree() {}

  static List<CoreDefinitionLink> links(CoreDefinition definition) {
    List<CoreDefinitionLink> result = new ArrayList<>();
    new CoreWalker() {
      @Override
      protected void visitLink(CoreDefinitionLink link) {
        result.add(link);
      }
    }.walk(definition);
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
