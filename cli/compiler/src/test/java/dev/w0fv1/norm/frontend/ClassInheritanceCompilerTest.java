package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ClassInheritanceCompilerTest {
  @Test
  void supportsNominalClassBoundsOnTypeParameters() {
    CompilationResult accepted =
        compile(
            "class Base { public String name() { return \"base\" } } "
                + "class Child extends Base { Child() { super() } "
                + "public String name() { return \"child\" } } "
                + "String nameOf<T extends Base>(T value) { return value.name() } "
                + "Void main() { String name = nameOf(value: Child()) } ");
    CompilationResult rejected =
        compile(
            "class Base {} class Plain {} "
                + "T keep<T extends Base>(T value) { return value } "
                + "Void main() { Plain value = keep(value: Plain()) } ");

    assertTrue(accepted.isSuccess(), () -> accepted.diagnostics().toString());
    assertFalse(rejected.isSuccess());
  }

  @Test
  void compilesExplicitConstructorsAndSingleInheritance() {
    CompilationResult result =
        compile(
            "class Base { String name Base(String initial) { name = initial } "
                + "public String label() { return name } } "
                + "class Child extends Base { Integer rank "
                + "Child(String initial, Integer initialRank) { "
                + "super(initial: initial) rank = initialRank } "
                + "public String label() { return name } } "
                + "Void main() { Base value = Child(initial: \"n\", initialRank: 1) "
                + "printLine(value.label()) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void resolvesOverloadedConstructorsAndSuperCalls() {
    CompilationResult result =
        compile(
            "class Base { String name "
                + "Base(String value) { name = value } "
                + "Base(Integer value) { name = value.toString() } } "
                + "class User extends Base { "
                + "User(String value) { super(value: value) } "
                + "User(Integer value) { super(value: value) } } "
                + "Void main() { User first = User(value: \"Norm\") "
                + "User second = User(value: 7) printLine(first.name) printLine(second.name) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsConstructorThatLeavesAFieldUninitialized() {
    CompilationResult result =
        compile("class Point { Integer x Integer y Point(Integer initial) { x = initial } } ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("field 'y'")),
        () -> result.diagnostics().toString());
  }

  @Test
  void constructorParametersMayShadowFieldsThroughThis() {
    CompilationResult result =
        compile(
            "class Point { Integer x Point(Integer x) { this.x = x } } "
                + "Void main() { Point point = Point(x: 1) printLine(point.x) }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void parameterAssignmentDoesNotInitializeShadowedField() {
    CompilationResult result = compile("class Point { Integer x Point(Integer x) { x = x } }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("field 'x'")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsUninitializedReturnNestedInLoop() {
    CompilationResult result =
        compile("class Point { Integer x Point(Boolean stop) { " + "for stop { return } x = 1 } }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("field 'x'")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsFieldReadsWhileEvaluatingSuperArguments() {
    CompilationResult result =
        compile(
            "class Base { Integer value Base(Integer initial) { value = initial } } "
                + "class Child extends Base { Child() { super(initial: value) } }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("read before initialization")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsSelfEscapeBeforeSuperEvenWithoutFields() {
    CompilationResult result =
        compile(
            "class Base { Base(Child child) {} } "
                + "class Child extends Base { Child() { super(child: this) } }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("before initialization")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsSelfEscapeBeforeInitializationCompletes() {
    CompilationResult result =
        compile(
            "Void consume(Point value) {} "
                + "class Point { Integer x Point() { consume(value: this) x = 1 } }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("before initialization")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsLambdaCaptureBeforeInitializationCompletes() {
    CompilationResult result =
        compile(
            "class Point { Integer x Point() { "
                + "Function<Void()> read = () { printLine(x) } read() x = 1 } }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("before initialization")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsFieldReadsBeforeInitialization() {
    CompilationResult result =
        compile("class Point { Integer x Integer y Point() { y = x x = 1 } }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("read before initialization")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsValueConstructors() {
    CompilationResult result =
        compile("value Point { Integer x Point(Integer initial) { x = initial } } ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("value")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsInheritanceFromValue() {
    CompilationResult result =
        compile("value Data { Integer value } class Invalid extends Data {} ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("class")),
        () -> result.diagnostics().toString());
  }

  @Test
  void requiresSubclassConstructorToCallSuper() {
    CompilationResult result =
        compile(
            "class Base { Integer value Base(Integer initial) { value = initial } } "
                + "class Child extends Base { Child(Integer initial) {} } ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("super")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsInheritanceCycles() {
    CompilationResult result =
        compile("class First extends Second {} class Second extends First {} ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("cycle")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsIncompatiblePublicOverrides() {
    CompilationResult result =
        compile(
            "class Base { public String label() { return \"base\" } } "
                + "class Child extends Base { public Integer label() { return 1 } } ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("override")),
        () -> result.diagnostics().toString());
  }

  @Test
  void requiresOverrideTypeParameterBoundsToMatch() {
    CompilationResult mismatched =
        compile(
            "interface Sized { Integer size() } "
                + "class Base { public T keep<T extends Sized>(T value) { return value } } "
                + "class Child extends Base { Child() { super() } "
                + "public T keep<T>(T value) { return value } } Void main() {} ");
    CompilationResult alphaEquivalent =
        compile(
            "interface Related<T> {} "
                + "class Base<T> { "
                + "public U keep<U extends Related<T>>(U value) { return value } } "
                + "class Child<T> extends Base<T> { Child() { super() } "
                + "public V keep<V extends Related<T>>(V value) { return value } } Void main() {} ");

    assertFalse(mismatched.isSuccess());
    assertTrue(
        mismatched.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("override")),
        () -> mismatched.diagnostics().toString());
    assertTrue(alphaEquivalent.isSuccess(), () -> alphaEquivalent.diagnostics().toString());
  }

  @Test
  void rejectsConstructorVisibilityModifiers() {
    CompilationResult result = compile("class Point { public Point() {} } Void main() {} ");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("constructor visibility")),
        () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new CompilerSession().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
