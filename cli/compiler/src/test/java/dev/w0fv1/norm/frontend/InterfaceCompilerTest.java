package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class InterfaceCompilerTest {
  @Test
  void compilesGenericInterfacesExplicitConformancesAndBounds() {
    CompilationResult result =
        compile(
            "interface Comparable<T> { Integer compareTo(T right) } "
                + "interface Named { String name() } "
                + "interface OrderedNamed<T> extends Comparable<T>, Named {} "
                + "class Item implements OrderedNamed<Item> { Integer value "
                + "public Integer compareTo(Item right) { return value - right.value } "
                + "public String name() { return \"item\" } } "
                + "T larger<T extends Comparable<T>>(T left, T right) { "
                + "if left.compareTo(right: right) >= 0 { return left } return right } "
                + "Void main() { Item left = Item(value: 2) Item right = Item(value: 4) "
                + "Item value = larger(left: left, right: right) printLine(value.value) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void assignsAnExtendedInterfaceToItsParentType() {
    CompilationResult result =
        compile(
            "interface Parent {} interface Child extends Parent {} "
                + "Parent widen(Child value) { return value } Void main() {} ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void specializesReceiverDependentBoundsOfInheritedDefaults() {
    CompilationResult result =
        compile(
            "interface Related<T> {} "
                + "interface Parent<Left, Right> { "
                + "U keep<U extends Related<Right>>(U value) { return value } } "
                + "interface Child<T> extends Parent<Integer, T> {} "
                + "class Token implements Related<String> {} "
                + "class Service implements Child<String> {} "
                + "Void main() { Service service = Service() Token token = Token() "
                + "Token kept = service.keep(value: token) } ");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void requiresExplicitNominalConformance() {
    CompilationResult result =
        compile(
            "interface Named { String name() } "
                + "class Accidental { public String name() { return \"value\" } } "
                + "String read(Named value) { return value.name() } "
                + "Void main() { printLine(read(value: Accidental())) } ");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsMissingPrivateAndMismatchedWitnesses() {
    CompilationResult missing =
        compile(
            "interface Sized { Integer size() } class Missing implements Sized {} Void main() {} ");
    CompilationResult hidden =
        compile(
            "interface Sized { Integer size() } class Hidden implements Sized { "
                + "private Integer size() { return 0 } } Void main() {} ");
    CompilationResult mismatched =
        compile(
            "interface Sized { Integer size() } class Wrong implements Sized { "
                + "public String size() { return \"0\" } } Void main() {} ");

    assertFalse(missing.isSuccess());
    assertFalse(hidden.isSuccess());
    assertFalse(mismatched.isSuccess());
  }

  @Test
  void permitsCovariantInterfaceMethodReturns() {
    CompilationResult narrower =
        compile(
            "interface Named { String? name() } "
                + "class Present implements Named { public String name() { return \"value\" } } "
                + "Void main() {} ");
    CompilationResult wider =
        compile(
            "interface Named { String name() } "
                + "class Maybe implements Named { public String? name() { return null } } "
                + "Void main() {} ");

    assertTrue(narrower.isSuccess(), () -> narrower.diagnostics().toString());
    assertFalse(wider.isSuccess());
  }

  @Test
  void rejectsInterfaceCyclesAndConflictingInstantiations() {
    CompilationResult cycle =
        compile("interface Left extends Right {} interface Right extends Left {} Void main() {} ");
    CompilationResult conflict =
        compile(
            "interface Value<T> { T value() } "
                + "interface IntegerValue extends Value<Integer> {} "
                + "interface StringValue extends Value<String> {} "
                + "class Both implements IntegerValue, StringValue { "
                + "public Integer value() { return 1 } } Void main() {} ");

    assertFalse(cycle.isSuccess());
    assertFalse(conflict.isSuccess());
  }

  @Test
  void rejectsTypeArgumentsThatDoNotSatisfyBounds() {
    CompilationResult result =
        compile(
            "interface Sized { Integer size() } "
                + "T keep<T extends Sized>(T value) { return value } "
                + "class Plain {} Void main() { Plain value = keep(value: Plain()) } ");

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsFieldsAndAcceptsDefaultMethodsInInterfaces() {
    CompilationResult field = compile("interface Invalid { Integer value } Void main() {} ");
    CompilationResult body =
        compile("interface Invalid { Integer value() { return 1 } } Void main() {} ");

    assertFalse(field.isSuccess());
    assertTrue(body.isSuccess(), () -> body.diagnostics().toString());
  }

  @Test
  void matchesInterfaceWitnessTypeParameterBoundsByShape() {
    CompilationResult mismatched =
        compile(
            "interface Sized { Integer size() } "
                + "interface Keeper { T keep<T extends Sized>(T value) } "
                + "class Invalid implements Keeper { "
                + "public T keep<T>(T value) { return value } } Void main() {} ");
    CompilationResult alphaEquivalent =
        compile(
            "interface Related<T> {} "
                + "interface Keeper<T> { U keep<U extends Related<T>>(U value) } "
                + "class Valid<T> implements Keeper<T> { "
                + "public V keep<V extends Related<T>>(V value) { return value } } Void main() {} ");

    assertFalse(mismatched.isSuccess());
    assertTrue(alphaEquivalent.isSuccess(), () -> alphaEquivalent.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new CompilerSession().compile(SourceFile.of(Path.of("test.norm"), text));
  }
}
