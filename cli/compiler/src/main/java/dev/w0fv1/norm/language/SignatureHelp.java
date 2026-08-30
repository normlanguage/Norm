package dev.w0fv1.norm.language;

import java.util.List;

public record SignatureHelp(
    List<SignatureInformation> signatures, int activeSignature, int activeParameter) {
  public SignatureHelp {
    signatures = List.copyOf(signatures);
    if (signatures.isEmpty()) throw new IllegalArgumentException("signatures must not be empty");
    if (activeSignature < 0 || activeSignature >= signatures.size()) {
      throw new IllegalArgumentException("active signature is outside the signature list");
    }
    int parameters = signatures.get(activeSignature).parameters().size();
    if (activeParameter < 0 || activeParameter >= Math.max(1, parameters)) {
      throw new IllegalArgumentException("active parameter is outside the parameter list");
    }
  }
}
