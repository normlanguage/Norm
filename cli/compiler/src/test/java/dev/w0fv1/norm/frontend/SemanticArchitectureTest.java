package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SemanticArchitectureTest {
  @Test
  void givesOverloadsAndInterfaceMethodsDistinctParameterIdentities() {
    String source =
        "interface First { T read<T>(T value) } interface Second { T read<T>(T value) } "
            + "T read<T>(T value) { return value } T read<T>(T value, Integer count) { return value } Void main() {}";
    try (var compiler = new CompilerSession()) {
      var analysis =
          compiler.analyze(SourceFile.of(DocumentId.of("untitled:overload-identities"), source));
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      var parameters =
          analysis.semanticModel().symbols().stream()
              .filter(symbol -> symbol.name().equals("read") && symbol.declaration().isPresent())
              .map(symbol -> symbol.typeParameters().getFirst().type().identity())
              .toList();
      assertEquals(4, parameters.size());
      assertEquals(4, parameters.stream().distinct().count());
    }
  }

  @Test
  void joinsRelatedNominalConstraintsRegardlessOfArgumentOrder() {
    for (String arguments :
        List.of("first: Parent(), second: Child()", "first: Child(), second: Parent()")) {
      String source =
          "class Parent {} class Child extends Parent { Child() { super() } } "
              + "T choose<T>(T first, T second) { return first } Void main() { var value = choose("
              + arguments
              + ") }";
      try (var compiler = new CompilerSession()) {
        var result =
            compiler.compile(SourceFile.of(DocumentId.of("untitled:nominal-join"), source));
        assertTrue(result.isSuccess(), result.diagnostics().toString());
      }
    }
  }

  @Test
  void isolatesTypeParametersAcrossOwnersAndOverloads() {
    String source =
        "interface Left { Integer left() } "
            + "class First { T keep<T extends Left>(T value) { return value } } "
            + "class Second { Integer keep<T>(T value) { return value.left() } } Void main() {}";
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(SourceFile.of(DocumentId.of("untitled:identities"), source));
      assertTrue(analysis.hasErrors());
      var parameters =
          analysis.semanticModel().symbols().stream()
              .filter(symbol -> symbol.kind() == SymbolKind.METHOD && symbol.name().equals("keep"))
              .map(symbol -> symbol.typeParameters().getFirst().type().identity())
              .toList();
      assertEquals(2, parameters.stream().distinct().count());
      assertFalse(
          compiler
              .compile(SourceFile.of(DocumentId.of("untitled:identities"), source))
              .isSuccess());
    }
  }

  @Test
  void infersInheritedClassAndInterfaceArguments() {
    for (String parent : List.of("class Parent<T> {}", "interface Parent<T> {}")) {
      String relation = parent.startsWith("class") ? "extends" : "implements";
      String constructor = relation.equals("extends") ? "Child() { super() }" : "";
      String source =
          parent
              + " class Child "
              + relation
              + " Parent<Integer> { "
              + constructor
              + " } "
              + "Void accept<T>(Parent<T> value) {} Void main() { accept(Child()) }";
      try (var compiler = new CompilerSession()) {
        var result = compiler.compile(SourceFile.of(DocumentId.of("untitled:inference"), source));
        assertTrue(result.isSuccess(), result.diagnostics().toString());
      }
    }
  }

  @Test
  void preservesNullableFlowAcrossEveryLoopExit() {
    for (String loop : List.of("for Integer item : values", "for condition")) {
      String source =
          "Void use(Integer? value, List<Integer> values, Boolean condition) { "
              + loop
              + " { if value == null { return } } Integer result = value } Void main() {}";
      try (var compiler = new CompilerSession()) {
        var result = compiler.compile(SourceFile.of(DocumentId.of("untitled:loop"), source));
        assertFalse(result.isSuccess());
        assertTrue(
            result.diagnostics().stream().anyMatch(d -> d.code().value().equals("NORM-NULL-0001")));
      }
    }
  }

  @Test
  void rejectsNullableReceiversBeforeBinding() {
    for (String operation :
        List.of("for Integer item : values {}", "Integer value = values[0]", "values[0] = 1")) {
      try (var compiler = new CompilerSession()) {
        var result =
            compiler.compile(
                SourceFile.of(
                    DocumentId.of("untitled:receiver"),
                    "Void main() { List<Integer>? values = null " + operation + " }"));
        assertFalse(result.isSuccess());
      }
    }
  }

  @Test
  void sharesNominalRelationsWithSemanticQueries() {
    String source =
        "interface Contract {} class Parent implements Contract {} "
            + "class Child extends Parent { Child() { super() } } Void main() {}";
    try (var compiler = new CompilerSession()) {
      var analysis = compiler.analyze(SourceFile.of(DocumentId.of("untitled:relations"), source));
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      var model = analysis.semanticModel();
      SemanticType parent =
          model.symbols().stream()
              .filter(s -> s.name().equals("Parent") && s.kind() == SymbolKind.TYPE)
              .findFirst()
              .orElseThrow()
              .type();
      SemanticType child =
          model.symbols().stream()
              .filter(s -> s.name().equals("Child") && s.kind() == SymbolKind.TYPE)
              .findFirst()
              .orElseThrow()
              .type();
      SemanticType contract =
          model.symbols().stream()
              .filter(s -> s.name().equals("Contract") && s.kind() == SymbolKind.INTERFACE)
              .findFirst()
              .orElseThrow()
              .type();
      assertTrue(model.isAssignable(parent, child));
      assertTrue(model.isAssignable(contract, child));
      assertFalse(model.isAssignable(parent, child.nullable()));
      assertTrue(model.isAssignable(parent.nullable(), child.nullable()));
      assertFalse(model.isAssignable(SemanticType.parameter("other/T", "T"), child));
    }
  }
}
