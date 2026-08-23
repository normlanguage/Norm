package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import java.util.List;
import java.util.Optional;

final class SignatureHelpResolver {
  private final CallSiteResolver callSites = new CallSiteResolver();

  Optional<SignatureHelp> resolve(DocumentSemanticModel document, int offset) {
    if (offset < 0 || offset > document.source().length()) {
      throw new IllegalArgumentException("signature-help offset is outside the source");
    }
    Optional<CallSite> resolved = callSites.resolve(document, offset);
    if (resolved.isEmpty()) return Optional.empty();
    CallSite call = resolved.orElseThrow();
    Symbol symbol = call.callable();
    List<Symbol> candidates =
        (symbol.owner().isPresent()
                ? document.semanticModel().symbols().stream()
                    .filter(candidate -> candidate.owner().equals(symbol.owner()))
                : document.semanticModel().visibleSymbols(offset).stream())
            .filter(candidate -> candidate.name().equals(symbol.name()))
            .filter(
                candidate ->
                    candidate.kind() == symbol.kind()
                        || candidate.kind() == SymbolKind.FUNCTION
                            && symbol.kind() == SymbolKind.FUNCTION)
            .map(candidate -> candidate.id().equals(symbol.id()) ? symbol : candidate)
            .toList();
    if (candidates.isEmpty()) candidates = List.of(symbol);
    List<SignatureInformation> signatures =
        candidates.stream().map(SignatureHelpResolver::signature).toList();
    int activeSignature = 0;
    for (int index = 0; index < candidates.size(); index++) {
      if (candidates.get(index).id().equals(symbol.id())) {
        activeSignature = index;
        break;
      }
    }
    return Optional.of(new SignatureHelp(signatures, activeSignature, call.activeParameter()));
  }

  private static SignatureInformation signature(Symbol symbol) {
    return new SignatureInformation(
        SymbolPresentation.signature(symbol),
        symbol.documentation(),
        symbol.parameters().stream()
            .map(
                parameter ->
                    new ParameterInformation(
                        parameter.type().displayName() + " " + parameter.name(), ""))
            .toList());
  }
}
