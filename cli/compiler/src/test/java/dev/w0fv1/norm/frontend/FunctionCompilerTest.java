package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.core.CoreBindingKind;
import dev.w0fv1.norm.core.CoreDefinitionRole;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class FunctionCompilerTest {
  @Test
  void keepsDeclaredGenericReturnTypesForLambdasWithExplicitReturns() {
    var result =
        compile(
            """
            T run<T>(T value) {
              Function<T()> work = () { return value }
              work()
            }
            Void main() { String result = run("Todo") }
            """);
    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void acceptsExplicitTypeArgumentsBeforeTrailingLambdas() {
    var result =
        compile(
            """
            T submit<T>(Function<T()> work) { work() }
            class Runner {
              T run<T>(Function<T()> work) { work() }
            }
            Void main() {
              Integer count = submit<Integer> { 42 }
              List<String> names = submit<List<String>> { ["Norm"] }
              String title = Runner().run<String> { "Todo" }
            }
            """);
    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void checksNestedConcreteCallsWithoutLosingContextualArgumentTypes() {
    String expression = "[]";
    for (int depth = 0; depth < 6; depth++) expression = "pass(" + expression + ")";
    var result =
        compile(
            "List<Integer> pass(List<Integer> items) { items } Void main() { List<Integer> items = "
                + expression
                + " }");
    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    var invalid =
        compile(
            "List<Integer> pass(List<Integer> items) { items } Void main() {"
                + " pass(pass([\"wrong\"])) }");
    assertFalse(invalid.isSuccess());
  }

  @Test
  void checksIfExpressionConditionsBranchesAndScopes() {
    assertTrue(compile("Void main() { Integer value = if true { 1 } else { 2 } }").isSuccess());
    assertFalse(compile("Void main() { Integer value = if true { 1 } }").isSuccess());
    assertFalse(compile("Void main() { Integer value = if 1 { 1 } else { 2 } }").isSuccess());
    assertFalse(
        compile("Void main() { Integer value = if true { 1 } else { \"wrong\" } }").isSuccess());
    assertFalse(
        compile("Void main() { Integer value = if true { Integer local = 1 } else { 2 } }")
            .isSuccess());
    assertFalse(
        compile(
                "Void main() { Integer value = if true { Integer local = 1; local } else { local }"
                    + " }")
            .isSuccess());
  }

  @Test
  void checksImplicitReturnTypesAndAllCompletingPaths() {
    assertTrue(compile("Integer value() { 3 } Void main() { printLine(value()) }").isSuccess());
    assertFalse(compile("Integer value() { \"wrong\" } Void main() {}").isSuccess());
    assertFalse(
        compile("Integer value(Boolean flag) { if flag { 3 } } Void main() {}").isSuccess());
    assertFalse(compile("Integer value() { Integer local = 3 } Void main() {}").isSuccess());
    assertFalse(
        compile("Integer value(Boolean flag) { if flag { 3 } else { \"wrong\" } } Void main() {}")
            .isSuccess());
    assertTrue(compile("Void action() { 3 } Void main() { action() }").isSuccess());
    assertFalse(
        compile("Void main() { Function<Integer(Boolean)> choose = (flag) { if flag { 3 } } }")
            .isSuccess());
    assertFalse(
        compile(
                "Void main() { Function<Integer(Boolean)> choose = (flag) { if flag { 3 } else {"
                    + " \"wrong\" } } }")
            .isSuccess());
  }

  @Test
  void preservesWritablePropertyContractsAcrossInheritance() {
    String parent = "class Base { Any value { get { return 1 } set(next) {} } } ";
    assertFalse(
        compile(
                parent
                    + "class Child extends Base { Child() { super() } private Any value { get {"
                    + " return 1 } set(next) {} } } Void main() {}")
            .isSuccess());
    assertTrue(
        compile(
                "class Base<T> { private T stored Base(T initial) { stored = initial } T value {"
                    + " get { return stored } set(next) { stored = next } } } class Child extends"
                    + " Base<String> { Child() { super(initial: \"x\") } String value { get {"
                    + " return \"x\" } set(replacement) {} } } Void main() {}")
            .isSuccess());
    assertFalse(
        compile(
                parent
                    + "class Child extends Base { Child() { super() } String value { get { return"
                    + " \"x\" } set(next) {} } } Void main() {}")
            .isSuccess());
    assertFalse(
        compile(
                parent
                    + "class Child extends Base { Child() { super() } Any value { get { return 1 }"
                    + " } } Void main() {}")
            .isSuccess());
    assertFalse(
        compile(
                parent
                    + "class Child extends Base { Child() { super() } Any value { get { return 1 }"
                    + " private set(next) {} } } Void main() {}")
            .isSuccess());
    assertTrue(
        compile(
                parent
                    + "class Child extends Base { Child() { super() } Any value { get { return 1 }"
                    + " set(next) {} } } Void main() {}")
            .isSuccess());
    assertTrue(
        compile(
                "class Base { Any value { get { return 1 } } } class Child extends Base { Child() {"
                    + " super() } String value { get { return \"x\" } } } Void main() {}")
            .isSuccess());
  }

  @Test
  void rejectsImplicitReadOnlyWritesAndNonCallableProperties() {
    assertFalse(
        compile(
                "class Box { Integer value { get { return 1 } } Void change() { value = 2 } } Void"
                    + " main() {}")
            .isSuccess());
    assertFalse(
        compile(
                "class Box { Integer value { get { return 1 } } Integer read() { return value() } }"
                    + " Void main() {}")
            .isSuccess());
    assertFalse(
        compile(
                "class Base { Integer value { get { return 1 } private set(next) {} } } class Child"
                    + " extends Base { Child() { super() } Void change() { value = 2 } } Void"
                    + " main() {}")
            .isSuccess());
  }

  @Test
  void rejectsFieldPropertyConflictsAcrossInheritance() {
    var propertyOverField =
        compile(
            """
            class Parent { Integer value }
            class Child extends Parent {
              Child() { super(value: 1) }
              Integer value { get { return 2 } }
            }
            Void main() {}
            """);
    var fieldOverProperty =
        compile(
            """
            class Parent { Integer value { get { return 1 } } }
            class Child extends Parent {
              Integer value
              Child() { super() value = 2 }
            }
            Void main() {}
            """);
    assertFalse(propertyOverField.isSuccess());
    assertFalse(fieldOverProperty.isSuccess());
  }

  @Test
  void rejectsAPropertyThatConflictsWithAStorageField() {
    var conflict =
        compile("class Box { Integer value Integer value { get { return 1 } } } Void main() {} ");
    assertFalse(conflict.isSuccess());
  }

  @Test
  void rejectsReadOnlyPropertiesAndInaccessibleSetters() {
    var readOnly =
        compile("class Box { Integer value { get { return 1 } } } Void main() { Box().value = 2 }");
    var privateSetter =
        compile(
            "class Box { Integer value { get { return 1 } private set(next) {} } } Void main() {"
                + " Box().value = 2 }");
    assertFalse(readOnly.isSuccess());
    assertFalse(privateSetter.isSuccess());
  }

  @Test
  void rejectsPropertyWritesThroughNullableReceivers() {
    String declaration = "class Box { Integer value { get { return 1 } set(next) {} } } ";
    assertFalse(
        compile(declaration + "Void change(Box? box) { box.value = 2 } Void main() {} ")
            .isSuccess());
    assertFalse(
        compile(declaration + "Void change(Box? box) { box?.value = 2 } Void main() {} ")
            .isSuccess());
  }

  @Test
  void rejectsDuplicateAndNonCallableTrailingArguments() {
    var duplicate =
        compile("Void run(Function<Void()> body) {} Void main() { run(body: () {}) {} }");
    var nonCallable = compile("Void run(Integer value) {} Void main() { run() { 1 } }");
    assertFalse(duplicate.isSuccess());
    assertTrue(
        duplicate.diagnostics().stream()
            .anyMatch(value -> value.message().contains("supplied more than once")));
    assertFalse(nonCallable.isSuccess());
    assertTrue(
        nonCallable.diagnostics().stream()
            .anyMatch(value -> value.message().contains("requires a function parameter")));
  }

  @Test
  void treatsOmittedTopLevelReturnsAsVoid() {
    var valid = compile("run() { return } discard<T>(T value) { } main() { run() discard(1) }");
    var invalid = compile("value() { return 1 } main() { }");

    assertTrue(valid.isSuccess(), () -> valid.diagnostics().toString());
    assertFalse(invalid.isSuccess());
    assertTrue(
        invalid.diagnostics().stream()
            .anyMatch(value -> value.message().contains("expected Void but found Integer")));
  }

  @Test
  void distinguishesFluentMethodsFromExplicitVoidMethods() {
    var fluent =
        compile(
            "class Counter { Integer value add(Integer amount) { value = value + amount } } "
                + "main() { Counter counter = Counter(value: 1) counter.add(2).add(3) }");
    var explicitVoid =
        compile(
            "class Counter { Void clear() { } } "
                + "main() { Counter counter = Counter() counter.clear().clear() }");
    var explicitResult = compile("class Counter { reset() { return Counter() } } main() { }");

    assertTrue(fluent.isSuccess(), () -> fluent.diagnostics().toString());
    assertFalse(explicitVoid.isSuccess());
    assertFalse(explicitResult.isSuccess());
  }

  @Test
  void rejectsReassignmentBeforeOrAfterCapture() {
    var before =
        compile(
            "Void main() { Integer factor = 2 factor = 3 "
                + "var multiply = (Integer value) { value * factor } }");
    var after =
        compile(
            "Void main() { Integer factor = 2 "
                + "var multiply = (Integer value) { value * factor } factor = 3 }");

    assertFalse(before.isSuccess());
    assertFalse(after.isSuccess());
    assertTrue(
        before.diagnostics().stream()
            .anyMatch(value -> value.message().contains("effectively final")));
    assertTrue(
        after.diagnostics().stream()
            .anyMatch(value -> value.message().contains("effectively final")));
  }

  @Test
  void rejectsAssignmentToCapturedLocalInsideLambda() {
    var compilation =
        compile(
            "Void main() { Integer factor = 2 "
                + "var multiply = (Integer value) { factor = value value } }");

    assertFalse(compilation.isSuccess());
    assertTrue(
        compilation.diagnostics().stream()
            .anyMatch(value -> value.message().contains("effectively final")));
  }

  @Test
  void requiresCompleteFunctionSignaturesAndLambdaArity() {
    var raw = compile("Void main() { Function value = (Integer item) { item } }");
    var arity =
        compile("Void main() { Function<Integer(Integer)> value = (left, right) { left } }");
    var voidParameter = compile("main() { Function<Void(Void)> invalid = (value) { return } }");

    assertFalse(raw.isSuccess());
    assertFalse(arity.isSuccess());
    assertFalse(voidParameter.isSuccess());
  }

  @Test
  void requiresDefaultParametersAndFieldsAfterRequiredOnes() {
    var function =
        compile(
            "String invalid(String optional = \"value\", String required) { return required } Void"
                + " main() {}");
    var field =
        compile("value Invalid { String optional = \"value\" String required } Void main() {}");

    assertFalse(function.isSuccess());
    assertFalse(field.isSuccess());
    assertTrue(
        function.diagnostics().stream()
            .anyMatch(value -> value.message().contains("required parameter follows a default")));
    assertTrue(
        field.diagnostics().stream()
            .anyMatch(value -> value.message().contains("required field follows a default")));
  }

  @Test
  void requiresExplicitResolutionForConflictingInterfaceDefaults() {
    var compilation =
        compile(
            "interface First { Integer value() { return 1 } } "
                + "interface Second { Integer value() { return 2 } } "
                + "class Both implements First, Second { } Void main() { }");

    assertFalse(compilation.isSuccess());
    assertTrue(
        compilation.diagnostics().stream()
            .anyMatch(value -> value.message().contains("default methods conflict")));
  }

  @Test
  void inheritsTheMostSpecificInterfaceDefault() {
    var compilation =
        compile(
            "interface Parent { Integer value() { return 1 } } "
                + "interface Child extends Parent { Integer value() { return 2 } } "
                + "class Implementation implements Child { } "
                + "Void main() { printLine(Implementation().value()) }");

    assertTrue(compilation.isSuccess(), compilation.diagnostics().toString());
  }

  @Test
  void checksNullableExtensionReceiversByTheirDeclaredParameterType() {
    var extension =
        compile(
            """
            extension String display(String? value) { value ?? "empty" }
            Void main() { String? text = null printLine(text.display()) }
            """);
    assertTrue(extension.isSuccess(), () -> extension.diagnostics().toString());
    var instance =
        compile(
            """
            class Item { String display() { "item" } }
            extension String display(Item? value) { "extension" }
            Void main() { Item? item = null printLine(item.display()) }
            """);
    assertFalse(instance.isSuccess());
    var callback =
        compile(
            """
            class Item { Function<Void()> action = () {} }
            Void main() { Item? item = null item.action() }
            """);
    assertFalse(callback.isSuccess());
  }

  @Test
  void resolvesGenericExtensionsFromTheReceiverAndKeepsOrdinaryFunctionsExplicit() {
    var extension =
        compile(
            "extension T echoed<T>(T value) { return value } "
                + "Void main() { String text = \"Norm\" String copy = text.echoed() }");
    var ordinary =
        compile(
            "String echoed(String value) { return value } "
                + "Void main() { String text = \"Norm\" String copy = text.echoed() }");

    assertTrue(extension.isSuccess(), () -> extension.diagnostics().toString());
    assertFalse(ordinary.isSuccess());
    var artifact = extension.output().orElseThrow().artifact();
    var binding =
        artifact.namespace().bindings().stream()
            .filter(value -> value.name().equals("echoed"))
            .findFirst()
            .orElseThrow();
    assertTrue(binding.kind() == CoreBindingKind.EXTENSION);
    assertTrue(
        artifact.authoring().occurrence(binding.occurrence()).orElseThrow().role()
            == CoreDefinitionRole.EXTENSION);
  }

  @Test
  void requiresAnExtensionReceiver() {
    var compilation = compile("extension String invalid() { return \"invalid\" } Void main() {}");

    assertFalse(compilation.isSuccess());
    assertTrue(
        compilation.diagnostics().stream()
            .anyMatch(value -> value.message().contains("receiver parameter")));
  }

  @Test
  void rejectsAmbiguousExtensionOverloads() {
    var compilation =
        compile(
            "interface First {} interface Second {} class Both implements First, Second {} "
                + "extension String label(First value) { return \"first\" } "
                + "extension String label(Second value) { return \"second\" } "
                + "Void main() { String label = Both().label() }");

    assertFalse(compilation.isSuccess());
    assertTrue(
        compilation.diagnostics().stream()
            .anyMatch(value -> value.message().contains("ambiguous")));
  }

  private CompilationResult compile(String text) {
    return new CompilerSession().compile(SourceFile.of(Path.of("functions.norm"), text));
  }
}
