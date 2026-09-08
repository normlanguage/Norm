package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.TestAbi;
import java.util.List;

public record TestIndex(List<Test> tests) {
  public TestIndex {
    tests = List.copyOf(tests);
  }

  public static TestIndex from(SemanticModel semantics) {
    return new TestIndex(
        semantics.annotations().applications().stream()
            .filter(
                application ->
                    semantics
                        .symbol(application.annotation())
                        .orElseThrow()
                        .type()
                        .identity()
                        .equals(TestAbi.IDENTITY))
            .filter(application -> application.target() instanceof AnnotationSite.Symbol)
            .map(
                application ->
                    new Test(
                        ((AnnotationSite.Symbol) application.target()).symbol(),
                        application.values().stream()
                            .map(AnnotationValue::value)
                            .filter(AnnotationValue.ListValue.class::isInstance)
                            .map(AnnotationValue.ListValue.class::cast)
                            .flatMap(list -> list.values().stream())
                            .map(AnnotationValue::value)
                            .filter(AnnotationDeclarationReference.class::isInstance)
                            .map(AnnotationDeclarationReference.class::cast)
                            .map(AnnotationDeclarationReference::target)
                            .distinct()
                            .toList()))
            .toList());
  }

  public List<SymbolId> forDeclaration(SymbolId declaration) {
    return tests.stream()
        .filter(test -> test.targets().contains(declaration))
        .map(Test::symbol)
        .toList();
  }

  public record Test(SymbolId symbol, List<SymbolId> targets) {
    public Test {
      targets = List.copyOf(targets);
    }
  }
}
