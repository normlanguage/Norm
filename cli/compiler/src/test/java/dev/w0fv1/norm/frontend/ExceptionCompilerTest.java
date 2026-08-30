package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ExceptionCompilerTest {
  private static final String EXCEPTION_ROOT =
      "package std.core "
          + "class Exception { String message Exception(String message) { this.message = message } } ";

  @Test
  void compilesThrowCatchFinallyAndNominalSubtypes() {
    CompilationResult result =
        compile(
            "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } "
                + "Void fail() { throw Failure(message: \"failed\") } "
                + "Void main() { try { fail() } catch Failure error { "
                + "printLine(error.message) } finally { printLine(\"done\") } }");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void acceptsThrowAsANonCompletingFunctionPath() {
    CompilationResult result =
        compile(
            "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } "
                + "Integer value(Boolean valid) { "
                + "if valid { return 1 } else { throw Failure(message: \"invalid\") } } "
                + "Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void acceptsThrowAsANonCompletingSwitchCase() {
    CompilationResult result =
        compile(
            "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } "
                + "Integer value(Boolean valid) { return switch valid { "
                + "case true { break 1 } case false { "
                + "throw Failure(message: \"invalid\") } } } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void rejectsThrowAndCatchTypesOutsideExceptionHierarchy() {
    CompilationResult thrown = compile("Void main() { throw 1 }");
    CompilationResult caught = compile("Void main() { try {} catch String error {} }");

    assertExceptionDiagnostic(thrown);
    assertExceptionDiagnostic(caught);
  }

  @Test
  void rejectsCatchBranchesCoveredByEarlierTypes() {
    CompilationResult result =
        compile(
            "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } "
                + "Void main() { try {} catch Exception error {} catch Failure failure {} }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("already covered")),
        () -> result.diagnostics().toString());
  }

  @Test
  void requiresCatchOrFinally() {
    CompilationResult result = compile("Void main() { try {} }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("catch or finally")),
        () -> result.diagnostics().toString());
  }

  @Test
  void keepsCatchBindingsLexicallyScoped() {
    CompilationResult result =
        compile("Void main() { try {} catch Exception error {} printLine(error.message) }");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("cannot find name 'error'")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsNullableCatchTypesAndGenericExceptionClasses() {
    CompilationResult nullable = compile("Void main() { try {} catch Exception? error {} }");
    CompilationResult generic =
        compile(
            "class Failure<T> extends Exception { "
                + "Failure(String message) { super(message: message) } } Void main() {}");

    assertExceptionDiagnostic(nullable);
    assertExceptionDiagnostic(generic);
  }

  @Test
  void rejectsForwardDeclaredGenericExceptionDescendants() {
    CompilationResult result =
        compile(
            "class GenericFailure<T> extends Failure { "
                + "GenericFailure(String message) { super(message: message) } } "
                + "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } Void main() {}");

    assertExceptionDiagnostic(result);
  }

  @Test
  void appliesFinallyInitializationToConstructorReturns() {
    CompilationResult result =
        compile(
            "class Holder { Integer value Holder() { "
                + "try { return } finally { this.value = 1 } } } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void preservesConstructorInitializationAcrossEmptyFinally() {
    CompilationResult result =
        compile(
            "class Holder { Integer value Holder() { "
                + "try { this.value = 1 } finally {} } } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void preservesConstructorInitializationWhenFinallyReturns() {
    CompilationResult result =
        compile(
            "class Holder { Integer value Holder() { "
                + "try { this.value = 1 } finally { return } } } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void entersConstructorCatchesWithTheThrownInitializationState() {
    CompilationResult result =
        compile(
            "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } "
                + "class Holder { Integer value Holder() { try { this.value = 1 "
                + "throw Failure(message: \"failed\") } catch Failure error { return } } } "
                + "Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void excludesConstructorReturnsReplacedByFinallyThrows() {
    CompilationResult result =
        compile(
            "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } "
                + "class Holder { Integer value Holder() { "
                + "try { return } finally { throw Failure(message: \"failed\") } } } "
                + "Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void checksFinallyAgainstAbruptCompletionPaths() {
    CompilationResult result =
        compile(
            "Void consume(String text) {} "
                + "Void run(String? text) { try { if text == null { return } } "
                + "finally { consume(text: text) } } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("String?")),
        () -> result.diagnostics().toString());
  }

  @Test
  void appliesFinallyWritesToTheNormalSuccessFlow() {
    CompilationResult result =
        compile(
            "Void consume(String text) {} "
                + "Void run(String? text) { try { if text == null { return } } "
                + "finally { text = null } consume(text: text) } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("String?")),
        () -> result.diagnostics().toString());
  }

  @Test
  void includesExceptionalEntriesInConstructorFinallyInitialization() {
    CompilationResult result =
        compile(
            "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } "
                + "Void fail() { throw Failure(message: \"failed\") } "
                + "class Holder { Integer value Holder() { try { fail() this.value = 1 } "
                + "finally { return } } } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("initialize field 'value'")),
        () -> result.diagnostics().toString());
  }

  @Test
  void propagatesSwitchReturnsThroughConstructorExpressions() {
    CompilationResult result =
        compile(
            "class Holder { Integer value Holder(Boolean flag) { "
                + "Integer ignored = switch flag { case true { return } "
                + "case false { break 1 } } this.value = 1 } } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("initialize field 'value'")),
        () -> result.diagnostics().toString());
  }

  @Test
  void routesSwitchReturnsThroughConstructorFinally() {
    CompilationResult result =
        compile(
            "class Holder { Integer value Holder(Boolean flag) { try { "
                + "Integer ignored = switch flag { case true { return } "
                + "case false { break 1 } } } finally { this.value = 1 } } } Void main() {}");

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void checksConstructorAssignmentLocationsBeforeAbruptValues() {
    CompilationResult result =
        compile(
            "class Failure extends Exception { "
                + "Failure(String message) { super(message: message) } } "
                + "class Box { Integer value } "
                + "class Holder { Box box Holder(Boolean flag) { "
                + "this.box.value = switch flag { "
                + "case true { throw Failure(message: \"boom\") } "
                + "case false { throw Failure(message: \"boom\") } } } } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("read before initialization")),
        () -> result.diagnostics().toString());
  }

  @Test
  void rejectsReturnsBeforeSuperInitialization() {
    CompilationResult result =
        compile(
            "class Base { Integer value Base(Integer value) { this.value = value } } "
                + "class Child extends Base { Child(Boolean flag) { "
                + "super(value: switch flag { case true { return } case false { break 1 } }) "
                + "} } Void main() {}");

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("before super initialization")),
        () -> result.diagnostics().toString());
  }

  @Test
  void reportsMalformedExceptionRootAsSourceDiagnostic() {
    CompilationResult result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("malformed-exception.norm"),
                    "package std.core class Exception {} Void main() {}"));

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("Exception root ABI")),
        () -> result.diagnostics().toString());
  }

  private static void assertExceptionDiagnostic(CompilationResult result) {
    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("Exception")),
        () -> result.diagnostics().toString());
  }

  private static CompilationResult compile(String text) {
    return new CompilerSession()
        .compile(SourceFile.of(Path.of("exceptions.norm"), EXCEPTION_ROOT + text));
  }
}
