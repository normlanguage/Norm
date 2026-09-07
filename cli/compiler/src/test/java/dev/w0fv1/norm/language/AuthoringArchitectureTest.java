package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceLocation;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

final class AuthoringArchitectureTest {
  @Test
  void exposesOneEffectiveMemberAcrossConformanceAndOverride() {
    String text =
        "interface Named { Integer label() } "
            + "class Base implements Named { public Integer label() { return 1 } } "
            + "class Child extends Base { Child() { super() } public Integer label() { return 2 } } "
            + "Void main() {}";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:members"), text));
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      var model = analysis.semanticModel();
      for (String name : java.util.List.of("Base", "Child")) {
        var owner =
            model.symbols().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
        var labels =
            model.members(owner.type()).stream().filter(s -> s.name().equals("label")).toList();
        assertEquals(1, labels.size(), name);
        assertEquals(owner.id(), labels.getFirst().owner().orElseThrow());
      }
    }
  }

  @Test
  void specializesExtensionReceiversAndChecksTheirBounds() {
    String text =
        "interface Contract {} class Parent implements Contract {} "
            + "class Child extends Parent { Child() { super() } } "
            + "extension T keep<T extends Contract>(T value) { return value } "
            + "extension Integer first<T>(List<T> values) { return 1 } "
            + "Void main() { Child value = Child() value. }";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:extensions"), text));
      var candidates = service.complete(analysis, text.lastIndexOf("value.") + 6);
      var keep =
          candidates.stream().filter(c -> c.label().equals("keep")).findFirst().orElseThrow();
      assertTrue(keep.detail().startsWith("Child "), keep.detail());
      assertTrue(candidates.stream().noneMatch(c -> c.label().equals("first")));
    }
  }

  @Test
  void keepsDynamicDispatchAcrossRename() {
    String source =
        "class Base { public Integer label() { return 1 } } "
            + "class Child extends Base { Child() { super() } public Integer label() { return 2 } } "
            + "Void main() { Base value = Child() printLine(value.label()) }";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:rename"), source));
      var edit = service.rename(analysis, source.indexOf("label"), "title").orElseThrow();
      var renamed = new StringBuilder(source);
      edit.locations().stream()
          .sorted(Comparator.comparingInt(SourceLocation::startOffset).reversed())
          .forEach(
              location ->
                  renamed.replace(location.startOffset(), location.endOffset(), edit.newName()));
      assertEquals("2", execute(source));
      assertEquals("2", execute(renamed.toString()));
      assertEquals(3, edit.locations().size());
    }
  }

  @Test
  void completesMalformedGenericApplicationsWithoutThrowing() {
    String text = "Void main() { List<Integer, String> values = List<Integer>() }";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:arity"), text));
      assertTrue(analysis.hasErrors());
      assertDoesNotThrow(() -> service.complete(analysis, text.lastIndexOf("List<Integer>()") + 2));
    }
  }

  @Test
  void completesExpressionsInsideInterpolation() {
    String text = "Void main() { Integer value = 1 printLine(\"${value}\") }";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:interpolation"), text));
      assertFalse(analysis.hasErrors());
      assertTrue(
          service.complete(analysis, text.indexOf("${value}") + 7).stream()
              .anyMatch(c -> c.label().equals("value")));
      assertTrue(service.complete(analysis, text.indexOf("${value}")).isEmpty());
    }
  }

  private static String execute(String text) {
    try (var compiler = new CompilerSession()) {
      var result = compiler.compile(SourceFile.of(DocumentId.of("untitled:execute"), text));
      assertTrue(result.isSuccess(), result.diagnostics().toString());
      var output = new StringWriter();
      new TruffleExecutionBackend()
          .execute(
              result.output().orElseThrow().artifact(),
              ExecutionContext.of(new PrintWriter(output)));
      return output.toString().strip();
    }
  }
}
