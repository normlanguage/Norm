package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

final class ContractRelations {
  private static final Comparator<Symbol> ORDER =
      Comparator.<Symbol, String>comparing(
              symbol ->
                  symbol
                      .declaration()
                      .map(location -> location.document().uri().toString())
                      .orElse(""))
          .thenComparingInt(
              symbol -> symbol.declaration().map(location -> location.startOffset()).orElse(-1));

  List<Symbol> requirements(SemanticModel model, Symbol implementation) {
    if (implementation.kind() != SymbolKind.METHOD || implementation.owner().isEmpty()) {
      return List.of();
    }
    return model.symbols().stream()
        .filter(symbol -> symbol.kind() == SymbolKind.INTERFACE_METHOD)
        .filter(
            requirement ->
                model
                    .witness(implementation.owner().orElseThrow(), requirement.id())
                    .filter(implementation.id()::equals)
                    .isPresent())
        .sorted(ORDER)
        .toList();
  }

  List<Symbol> related(SemanticModel model, Symbol selected) {
    List<Symbol> requirements =
        selected.kind() == SymbolKind.INTERFACE_METHOD
            ? List.of(selected)
            : requirements(model, selected);
    if (requirements.isEmpty()) return List.of(selected);
    LinkedHashMap<dev.w0fv1.norm.semantic.SymbolId, Symbol> related = new LinkedHashMap<>();
    requirements.forEach(requirement -> related.put(requirement.id(), requirement));
    model.symbols().stream()
        .filter(symbol -> symbol.kind() == SymbolKind.TYPE)
        .forEach(
            owner ->
                requirements.forEach(
                    requirement ->
                        model
                            .witness(owner.id(), requirement.id())
                            .flatMap(model::symbol)
                            .ifPresent(
                                implementation ->
                                    related.put(implementation.id(), implementation))));
    return related.values().stream().sorted(ORDER).toList();
  }
}
