package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.Symbol;
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
    List<SignatureInformation> signatures =
        call.callables().stream().map(SignatureHelpResolver::signature).toList();
    return Optional.of(
        new SignatureHelp(signatures, call.activeSignature(), call.activeParameter()));
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
