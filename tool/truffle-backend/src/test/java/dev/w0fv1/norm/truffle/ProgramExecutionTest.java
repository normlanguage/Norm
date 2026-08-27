package dev.w0fv1.norm.truffle;

import static dev.w0fv1.norm.testing.NormTestKit.projectSuite;
import static dev.w0fv1.norm.testing.NormTestKit.suite;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class ProgramExecutionTest {
  @Test
  void reflectsRuntimeTypeAnnotations() throws Exception {
    assertOutput(
        "annotation Label targets(type) retention(runtime) { String text } "
            + "annotation Internal targets(type) retention(binary) { String text } "
            + "@Label(text: \"point\") @Internal(text: \"hidden\") value Point { Integer x } "
            + "Void main() { Type<Point> type = reflect<Point>() "
            + "Label? label = type.annotation<Label>() "
            + "Internal? hidden = type.annotation<Internal>() "
            + "printLine(type.name()) printLine(label?.text ?? \"missing\") "
            + "printLine(hidden == null) }",
        String.join(System.lineSeparator(), "Point", "point", "true", ""));
  }

  @Test
  void reflectsGenericAndNullableDisplayNames() throws Exception {
    assertOutput(
        "Void main() { printLine(reflect<List<String>?>().name()) }",
        "List<String>?" + System.lineSeparator());
  }

  @Test
  void executesNominalCatchSelectionAndFinallyCompletion() throws Exception {
    assertOutput(
        "import std.core.Exception class Failure extends Exception { "
            + "Failure(String message) { super(message: message) } } "
            + "class SpecificFailure extends Failure { "
            + "SpecificFailure(String message) { super(message: message) } } "
            + "Void fail() { throw SpecificFailure(message: \"specific\") } "
            + "Void main() { try { fail() } catch SpecificFailure error { "
            + "printLine(error.message) } catch Failure error { printLine(\"failure\") } "
            + "finally { printLine(\"finally\") } try { fail() } catch Exception error { "
            + "printLine(\"root\") } }",
        String.join(System.lineSeparator(), "specific", "finally", "root", ""));
  }

  @Test
  void finallyRunsForReturnAndCanReplaceItWithThrow() throws Exception {
    assertOutput(
        "import std.core.Exception class Failure extends Exception { "
            + "Failure(String message) { super(message: message) } } "
            + "Integer choose() { try { return 1 } finally { printLine(\"cleanup\") } } "
            + "Void main() { printLine(choose()) try { try { return } finally { "
            + "throw Failure(message: \"replacement\") } } catch Failure error { "
            + "printLine(error.message) } }",
        String.join(System.lineSeparator(), "cleanup", "1", "replacement", ""));
  }

  @Test
  void finallyRunsAcrossLoopControl() throws Exception {
    assertOutput(
        "Void main() { for value : [1, 2, 3] { try { "
            + "if value == 1 { continue } if value == 2 { break } "
            + "} finally { printLine(value) } } }",
        String.join(System.lineSeparator(), "1", "2", ""));
  }

  @Test
  void exposesUncaughtGuestExceptionsWithSourceAndStack() {
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput(
                    "import std.core.Exception "
                        + "class Failure extends Exception { "
                        + "Failure(String message) { super(message: message) } } "
                        + "Void fail() { throw Failure(message: \"boom\") } "
                        + "Void main() { fail() }",
                    ""));

    assertEquals(RuntimeErrorCode.UNCAUGHT_EXCEPTION, exception.code());
    assertEquals("boom", exception.getMessage());
    assertTrue(exception.line() > 0);
    assertTrue(exception.guestStack().stream().anyMatch(frame -> frame.name().equals("fail")));
  }

  @Test
  void keepsRuntimeFailuresOutsideUserCatchClauses() {
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput(
                    "import std.core.Exception Void main() { "
                        + "try { printLine(1 / 0) } catch Exception error { "
                        + "printLine(error.message) } }",
                    ""));

    assertEquals(RuntimeErrorCode.DIVISION_BY_ZERO, exception.code());
  }

  @Test
  void readsWritesAndComparesReferenceLocations() throws Exception {
    assertOutput(
        "Void replace(ref<Integer> target, Integer value) { *target = value } "
            + "class Box { Integer value } "
            + "Void main() { Integer local = 1 ref<Integer> first = &local "
            + "ref<Integer> same = &local printLine(first == same) "
            + "replace(target: first, value: 7) printLine(local) "
            + "Box box = Box(value: 3) ref<Integer> field = &box.value "
            + "printLine(field == &box.value) replace(target: field, value: 9) "
            + "printLine(box.value) printLine(first == field) }",
        String.join(System.lineSeparator(), "true", "7", "true", "9", "false", ""));
  }

  @Test
  void dispatchesReferenceParametersThroughInterfaces() throws Exception {
    assertOutput(
        "interface Mutator { Void replace(ref<Integer> target, Integer value) } "
            + "class Replacer implements Mutator { public Void replace(ref<Integer> target, "
            + "Integer value) { *target = value } } "
            + "Void apply(Mutator mutator, ref<Integer> target) { "
            + "mutator.replace(target: target, value: 8) } "
            + "Void main() { Integer value = 1 Replacer replacer = Replacer() "
            + "apply(mutator: replacer, target: &value) printLine(value) }",
        "8" + System.lineSeparator());
  }

  @Test
  void materializesSequenceLiteralsAsTheirExpectedCollectionType() throws Exception {
    assertOutput(
        "Void main() { Array<Integer> array = [1, 2] List<Integer> list = [3, 4] "
            + "list.add(5) printLine(array.size()) printLine(list.size()) printLine(list[2]) }",
        String.join(System.lineSeparator(), "2", "3", "5", ""));
  }

  @Test
  void preservesConcreteLeavesInNumberCollectionLiterals() throws Exception {
    assertOutput(
        "Void main() { List<Number> values = [1, 2.5, 2147483648] "
            + "for value,index : values { printLine(index) printLine(value) } "
            + "var inferred = [1, 2.5, 2147483648] "
            + "printLine(inferred.size()) printLine(inferred[1]) }",
        String.join(
            System.lineSeparator(), "0", "1", "1", "2.5", "2", "2147483648", "3", "2.5", ""));
  }

  @Test
  void infersPrintableElementTypesThroughIterableConformance() throws Exception {
    assertOutput(
        "import std.io.printLines Void main() { printLines([4, 2, 4, -1, -1]) }",
        String.join(System.lineSeparator(), "4", "2", "4", "-1", "-1", ""));
  }

  @Test
  void printsHeterogeneousStringableValues() throws Exception {
    assertOutput(
        "import std.io.printLines Void main() { printLines([7, true, \"Norm\"]) }",
        String.join(System.lineSeparator(), "7", "true", "Norm", ""));
  }

  @Test
  void printsExplicitUserStringableImplementations() throws Exception {
    assertOutput(
        "import std.io.printLines import std.core.Stringable "
            + "class Label implements Stringable { String text "
            + "String toString() { return text } } "
            + "Void main() { printLines([Label(text: \"Norm\"), Label(text: \"Compiler\")]) }",
        String.join(System.lineSeparator(), "Norm", "Compiler", ""));
  }

  @Test
  void executesNestedDiamondConstructors() throws Exception {
    assertOutput(
        "Void main() { List<Pair<Integer, String>> values = List<>() "
            + "values.add(Pair<>(first: 7, second: \"seven\")) "
            + "printLine(values[0].first) printLine(values[0].second) }",
        String.join(System.lineSeparator(), "7", "seven", ""));
  }

  @Test
  void executesIndexedLoopsAcrossContinueAndBreak() throws Exception {
    assertOutput(
        "Void main() { for value,index : [10, 20, 30, 40] { "
            + "if index == 1 { continue } printLine(index) "
            + "if index == 2 { break } printLine(value) } }",
        String.join(System.lineSeparator(), "0", "10", "2", ""));
  }

  @Test
  void preservesSourceOrderWhenBindingNamedArguments() throws Exception {
    assertOutput(
        "class Counter { Integer value Integer next() { value = value + 1 return value } } "
            + "Integer combine(Integer left, Integer right) { return left * 10 + right } "
            + "Void main() { Counter counter = Counter(value: 0) "
            + "printLine(combine(right: counter.next(), left: counter.next())) }",
        "21" + System.lineSeparator());
  }

  @Test
  void givesClassesIdentityAndCopyCreatesANewIdentity() throws Exception {
    assertOutput(
        "class Box { Integer value Void set(Integer next) { value = next } } "
            + "class Holder { Box box } "
            + "Void main() { "
            + "Box first = Box(value: 4) Box shared = first shared.set(9) "
            + "printLine(first.value) printLine(first == shared) "
            + "Box copied = first.copy() copied.set(12) "
            + "printLine(first.value) printLine(copied.value) printLine(first == copied) "
            + "Holder holder = Holder(box: first) Holder holderCopy = holder.copy() "
            + "holderCopy.box.set(15) printLine(holder.box.value) }",
        String.join(System.lineSeparator(), "9", "true", "9", "12", "false", "15", ""));
  }

  @Test
  void executesExplicitConstructorsSuperCallsAndDynamicOverrides() throws Exception {
    assertOutput(
        "class Base { String name Base(String initial) { name = initial } "
            + "public String label() { return \"base:\" + name } } "
            + "class Child extends Base { Integer rank "
            + "Child(String initial, Integer initialRank) { "
            + "super(initial: initial) rank = initialRank } "
            + "public String label() { return \"child:\" + name } } "
            + "Void main() { Child child = Child(initial: \"norm\", initialRank: 9) "
            + "Base base = child printLine(base.label()) printLine(child.name) "
            + "printLine(child.rank) }",
        String.join(System.lineSeparator(), "child:norm", "norm", "9", ""));
  }

  @Test
  void preservesGenericParentViewsAndInterfaceDispatch() throws Exception {
    assertOutput(
        "interface Named { String name() } "
            + "class Base<T> implements Named { T value "
            + "Base(T initial) { value = initial } public T get() { return value } "
            + "public String name() { return \"base\" } } "
            + "class Child extends Base<String> { Integer rank "
            + "Child(String initial, Integer initialRank) { "
            + "super(initial: initial) rank = initialRank } "
            + "public String name() { return value } } "
            + "Void main() { Child child = Child(initial: \"norm\", initialRank: 9) "
            + "Base<String> base = child Named named = child "
            + "Function<String()> method = child::name "
            + "printLine(base.get()) printLine(named.name()) printLine(method()) }",
        String.join(System.lineSeparator(), "norm", "norm", "norm", ""));
  }

  @Test
  void copiesValueContainersButSharesTheirClassElements() throws Exception {
    assertOutput(
        "class Box { Integer value Void set(Integer next) { value = next } } "
            + "Void main() { List<Box> first = List<>() Box box = Box(value: 1) first.add(box) "
            + "List<Box> second = first second.add(Box(value: 2)) Box secondBox = second[0] secondBox.set(7) "
            + "Box firstBox = first[0] printLine(first.size()) printLine(second.size()) printLine(firstBox.value) "
            + "List<Box> same = List<>() same.add(box) printLine(first == same) }",
        String.join(System.lineSeparator(), "1", "2", "7", "true", ""));
  }

  @Test
  void comparesValueContainersStructurally() throws Exception {
    assertOutput(
        "Void main() { "
            + "List<Integer> left = List<>() left.add(1) List<Integer> right = List<>() right.add(1) printLine(left == right) "
            + "Pair<List<Integer>, Integer> first = Pair<>(first: left, second: 2) Pair<List<Integer>, Integer> second = Pair<>(first: right, second: 2) printLine(first == second) "
            + "Map<Array<Integer>, Integer> values = Map<>() values.put(key: [1, 2], value: 7) printLine(values[[1, 2]]) "
            + "Set<Array<Integer>> unique = Set<>() unique.add([3, 4]) printLine(unique.contains([3, 4])) }",
        String.join(System.lineSeparator(), "true", "true", "7", "true", ""));
  }

  @Test
  void bindsNamedArgumentsForBuiltins() throws Exception {
    assertOutput(
        "import std.math.min import std.math.max "
            + "Void main() { printLine(min(right: 8, left: 3)) printLine(max(right: 8, left: 3)) }",
        String.join(System.lineSeparator(), "3", "8", ""));
  }

  @Test
  void executesGenericCollectionsWithSize() throws Exception {
    assertOutput(
        "Void main() { List<Integer> values = List<>() values.add(3) values.add(8) "
            + "printLine(values.size()) printLine(values[1]) }",
        String.join(System.lineSeparator(), "2", "8", ""));
  }

  @Test
  void executesGenericFunctions() throws Exception {
    assertOutput(
        "T identity<T>(T value) { return value } "
            + "Void main() { printLine(identity(value: 9)) printLine(identity(value: \"Norm\")) }",
        String.join(System.lineSeparator(), "9", "Norm", ""));
  }

  @Test
  void constructsGenericDataEnumsAsStructuralValues() throws Exception {
    assertOutput(
        "enum Result<T, E> { Ok(T value), Err(E error) } "
            + "Void main() { "
            + "Result<Integer, String> first = Result<Integer, String>.Ok(value: 7) "
            + "Result<Integer, String> same = Result.Ok(value: 7) "
            + "Result<Integer, String> different = Result.Err(error: \"invalid\") "
            + "printLine(first) printLine(first == same) printLine(first == different) }",
        String.join(System.lineSeparator(), "Result.Ok(7)", "true", "false", ""));
  }

  @Test
  void executesRecursivePatternsAndEvaluatesTheScrutineeOnce() throws Exception {
    assertOutput(
        "enum Tree<T> { Leaf(T value), Branch(Tree<T> left, Tree<T> right) } "
            + "class Counter { Integer value Tree<Integer> next() { value = value + 1 "
            + "return Tree.Branch(left: Tree.Leaf(value: 2), right: Tree.Leaf(value: 3)) } } "
            + "Integer sum(Tree<Integer> tree) { return switch tree { "
            + "case Leaf(Integer value) { break value } "
            + "case Branch(Leaf(Integer left), Tree<Integer> right) { break left + sum(tree: right) } "
            + "case Branch(Tree<Integer> left, Tree<Integer> right) { break sum(tree: left) + sum(tree: right) } } } "
            + "Void main() { Counter counter = Counter(value: 0) "
            + "Integer total = switch counter.next() { "
            + "case Leaf(Integer value) { break value } "
            + "case Branch(Tree<Integer> left, Tree<Integer> right) { break sum(tree: left) + sum(tree: right) } } "
            + "printLine(total) printLine(counter.value) }",
        String.join(System.lineSeparator(), "5", "1", ""));
  }

  @Test
  void executesLiteralNullTypedAndWildcardPatterns() throws Exception {
    assertOutput(
        "String describe(Integer? value) { return switch value { "
            + "case null { break \"missing\" } case 0 { break \"zero\" } "
            + "case Integer number { break \"number\" } } } "
            + "String classify(String value) { return switch value { "
            + "case \"Norm\" { break \"language\" } case _ { break \"other\" } } } "
            + "Void main() { printLine(describe(value: null)) printLine(describe(value: 0)) "
            + "printLine(describe(value: 7)) printLine(classify(value: \"Norm\")) "
            + "printLine(classify(value: \"Java\")) }",
        String.join(System.lineSeparator(), "missing", "zero", "number", "language", "other", ""));
  }

  @Test
  void executesStatementSwitchWithoutFallthrough() throws Exception {
    assertOutput(
        "enum Choice { First, Second } Void main() { Choice choice = Choice.Second "
            + "switch choice { case First { printLine(\"first\") } "
            + "case Second { printLine(\"second\") } } printLine(\"done\") }",
        String.join(System.lineSeparator(), "second", "done", ""));
  }

  @Test
  void dispatchesInterfaceCallsByConcreteNominalType() throws Exception {
    assertOutput(
        "interface Named { String name() } "
            + "class User implements Named { String value public String name() { return value } } "
            + "class Service implements Named { String value public String name() { return \"service:\" + value } } "
            + "String read(Named value) { return value.name() } "
            + "Void main() { printLine(read(value: User(value: \"Norm\"))) "
            + "printLine(read(value: Service(value: \"compiler\"))) }",
        String.join(System.lineSeparator(), "Norm", "service:compiler", ""));
  }

  @Test
  void safelyDispatchesNullableInterfaceCalls() throws Exception {
    assertOutput(
        "interface Named { String name() } "
            + "class User implements Named { String value public String name() { return value } } "
            + "String read(Named? value) { return value?.name() ?? \"missing\" } "
            + "Void main() { Named? absent = null Named? present = User(value: \"Norm\") "
            + "printLine(read(value: absent)) printLine(read(value: present)) }",
        String.join(System.lineSeparator(), "missing", "Norm", ""));
  }

  @Test
  void preservesClassIdentityAcrossInterfaceAndBoundedCalls() throws Exception {
    assertOutput(
        "interface Mutable { Void set(Integer next) Integer get() } "
            + "class Box implements Mutable { Integer value "
            + "public Void set(Integer next) { value = next } public Integer get() { return value } } "
            + "Void update(Mutable value) { value.set(next: 9) } "
            + "T same<T extends Mutable>(T value) { value.set(next: 12) return value } "
            + "Void main() { Box box = Box(value: 1) update(value: box) "
            + "printLine(box.get()) Box result = same(value: box) "
            + "printLine(box.get()) printLine(result == box) }",
        String.join(System.lineSeparator(), "9", "12", "true", ""));
  }

  @Test
  void executesUserDefinedValueCopyEqualityHashAndInterfaceSemantics() throws Exception {
    assertOutput(
        "interface Named { String name() } "
            + "value Point implements Named { Integer x Integer y "
            + "public String name() { return \"point\" } } "
            + "value Bucket { List<Integer> values } "
            + "String read(Named value) { return value.name() } "
            + "Void main() { Point first = Point(x: 1, y: 2) Point second = first "
            + "printLine(first == second) printLine(read(value: second)) "
            + "Map<Point, String> names = Map<>() names.put(key: first, value: \"origin\") "
            + "printLine(names.get(key: second) ?? \"missing\") "
            + "List<Integer> source = [1] Bucket bucket = Bucket(values: source) source.add(2) "
            + "printLine(bucket.values.size()) }",
        String.join(System.lineSeparator(), "true", "point", "origin", "1", ""));
  }

  @Test
  void dispatchesBuiltinProtocolWitnesses() throws Exception {
    assertOutput(
        "import std.core.Sized "
            + "Integer readSize<T extends Sized>(T value) { return value.size() } "
            + "Void main() { List<Integer> values = List<>() values.add(3) values.add(7) "
            + "printLine(readSize(value: values)) }",
        "2" + System.lineSeparator());
  }

  @Test
  void dispatchesGenericInterfaceMethodsWithReifiedArguments() throws Exception {
    assertOutput(
        "interface Identity { T same<T>(T value) } "
            + "class IdentityService implements Identity { "
            + "public T same<T>(T value) { return value } } "
            + "String apply(Identity service) { return service.same<String>(value: \"Norm\") } "
            + "Void main() { printLine(apply(service: IdentityService())) }",
        "Norm" + System.lineSeparator());
  }

  @Test
  void iteratesUserDefinedIterableImplementations() throws Exception {
    assertOutput(
        "import std.core.Iterable import std.core.Iterator "
            + "class CountingIterator implements Iterator<Integer> { Integer current Integer end "
            + "public Boolean hasNext() { return current < end } "
            + "public Integer next() { Integer value = current current = current + 1 return value } } "
            + "class Values implements Iterable<Integer> { Integer end "
            + "public Iterator<Integer> iterator() { return CountingIterator(current: 0, end: end) } } "
            + "Void main() { for Integer value : Values(end: 4) { printLine(value) } }",
        String.join(System.lineSeparator(), "0", "1", "2", "3", ""));
  }

  @Test
  void iteratesBuiltinCollectionsThroughTheIterableInterface() throws Exception {
    assertOutput(
        "import std.core.Iterable "
            + "Void write(Iterable<Integer> values) { "
            + "for Integer value : values { printLine(value) } } "
            + "Void main() { List<Integer> values = List<>() "
            + "values.add(4) values.add(9) write(values: values) }",
        String.join(System.lineSeparator(), "4", "9", ""));
  }

  @Test
  void executesGenericMethodsWithInferredAndExplicitArguments() throws Exception {
    assertOutput(
        "class Values { T identity<T>(T value) { return value } } "
            + "Void main() { Values values = Values() "
            + "printLine(values.identity<Integer>(value: 9)) "
            + "printLine(values.identity(value: \"Norm\")) }",
        String.join(System.lineSeparator(), "9", "Norm", ""));
  }

  @Test
  void ordersOwnerAndMethodReifiedArguments() throws Exception {
    assertOutput(
        "class Values<T> { T owner Pair<T, U> pair<U>(U value) { "
            + "return Pair<>(first: owner, second: value) } } "
            + "Void main() { Values<String> values = Values<String>(owner: \"Norm\") "
            + "Pair<String, Integer> pair = values.pair(value: 9) "
            + "printLine(pair.first) printLine(pair.second) }",
        String.join(System.lineSeparator(), "Norm", "9", ""));
  }

  @Test
  void executesExplicitGenericBuiltinTypeMethods() throws Exception {
    assertOutput(
        "Void main() { List<Integer> values = "
            + "List.filled<Integer>(size: 2, value: 7) printLine(values[1]) }",
        "7" + System.lineSeparator());
  }

  @Test
  void mutatesValueFieldsThroughAClassMemberPath() throws Exception {
    assertOutput(
        "class Box<T> { T value } "
            + "Void main() { Box<List<Integer>> box = Box<>(value: List<>()) "
            + "box.value.add(9) printLine(box.value[0]) }",
        "9" + System.lineSeparator());
  }

  @Test
  void executesCoreTextAndMathOperations() throws Exception {
    assertOutput(
        "import std.math.clamp import std.math.sign "
            + "Void main() { printLine(\"A😀\".byteSize()) printLine(\"A😀\".codePointSize()) "
            + "printLine(\"👨‍👩‍👧‍👦\".graphemeSize()) printLine(clamp(value: 12, minimum: 0, maximum: 9)) "
            + "printLine(sign(-4)) }",
        String.join(System.lineSeparator(), "5", "2", "1", "9", "-1", ""));
  }

  @Test
  void exposesExplicitUnicodeStringViews() throws Exception {
    assertOutput(
        "Void main() { "
            + "Array<CodePoint> points = \"A😀\".codePoints() "
            + "Array<String> graphemes = \"é😀\".graphemes() "
            + "printLine(points.size()) printLine(points[0]) printLine(points[1]) "
            + "printLine(graphemes.size()) printLine(graphemes[0]) "
            + "printLine(\"A😀B\".sliceCodePoints(start: 1, end: 2)) "
            + "printLine('9'.scalarValue()) printLine(\"/a/b/\".split(separator: \"/\").size()) }",
        String.join(System.lineSeparator(), "2", "A", "😀", "2", "é", "😀", "57", "4", ""));
  }

  @Test
  void executesUnicodeStringOperations() throws Exception {
    assertOutput(
        "Void main() { "
            + "printLine(\"\".isEmpty()) printLine(\"Norm😀\".contains(value: \"rm😀\")) "
            + "printLine(\"Norm\".startsWith(prefix: \"No\")) printLine(\"Norm\".endsWith(suffix: \"rm\")) "
            + "printLine(\"a😀b\".sliceGraphemes(start: 1, end: 2)) "
            + "printLine(\"one two one\".replace(target: \"one\", replacement: \"1\")) "
            + "printLine(\"one two one\".replaceFirst(target: \"one\", replacement: \"1\")) "
            + "printLine(\"  Norm  \".trim()) printLine(\"  Norm  \".trimStart()) "
            + "printLine(\"  Norm  \".trimEnd()) printLine(\"NORM\".toLowercase()) "
            + "printLine(\"norm\".toUppercase()) printLine(\"straße\".toUppercase()) "
            + "printLine(\"Content-Type\".equalsIgnoreCaseAscii(other: \"content-type\")) "
            + "printLine(\"a😀\".compareCodePoints(right: \"a😁\")) "
            + "printLine('9'.isDecimalDigit()) printLine('字'.isLetter()) "
            + "printLine(' '.isWhitespace()) printLine('A'.isUppercase()) printLine('a'.isLowercase()) "
            + "printLine(\" Norm \".trim()) }",
        String.join(
            System.lineSeparator(),
            "true",
            "true",
            "true",
            "true",
            "😀",
            "1 two 1",
            "1 two one",
            "Norm",
            "Norm  ",
            "  Norm",
            "norm",
            "NORM",
            "STRASSE",
            "true",
            "-1",
            "true",
            "true",
            "true",
            "true",
            "true",
            "Norm",
            ""));
  }

  @Test
  void iteratesMapsAndStacksWithTheirDeclaredElementTypes() throws Exception {
    assertOutput(
        "Void main() { Map<String, Integer> values = Map<>() "
            + "values.put(key: \"first\", value: 1) values.put(key: \"second\", value: 2) "
            + "for Pair<String, Integer> entry : values { printLine(entry.first) printLine(entry.second) } "
            + "Stack<Integer> stack = Stack<>() stack.push(3) stack.push(7) "
            + "for Integer value : stack { printLine(value) } }",
        String.join(System.lineSeparator(), "first", "1", "second", "2", "7", "3", ""));
  }

  @Test
  void rejectsMissingMapKeysAtRuntime() {
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput(
                    "Void main() { Map<String, Integer> values = Map<>() printLine(values[\"missing\"]) }",
                    ""));
    assertTrue(exception.getMessage().contains("map key does not exist"));
    assertEquals(RuntimeErrorCode.MISSING_MAP_KEY, exception.code());
  }

  @Test
  void returnsNullableValuesFromMapGet() throws Exception {
    assertOutput(
        "Void main() { Map<String, Integer> values = Map<>() "
            + "values.put(key: \"answer\", value: 42) "
            + "printLine(values.get(key: \"answer\") ?? -1) "
            + "printLine(values.get(key: \"missing\") ?? -1) "
            + "Map<String, String?> nullable = Map<>() "
            + "nullable.put(key: \"saved\", value: null) "
            + "printLine(nullable.containsKey(key: \"saved\")) "
            + "printLine(nullable.get(key: \"saved\") == null) }",
        String.join(System.lineSeparator(), "42", "-1", "true", "true", ""));
  }

  @Test
  void executesNullFlowSafeAccessAndCoalescingWithShortCircuiting() throws Exception {
    assertOutput(
        "class User { String name } "
            + "String fallback() { printLine(\"fallback\") return \"guest\" } "
            + "Void main() { User? missing = null User? present = User(name: \"Norm\") "
            + "printLine(missing?.name ?? fallback()) "
            + "printLine(present?.name ?? fallback()) "
            + "if present != null { printLine(present.name) } }",
        String.join(System.lineSeparator(), "fallback", "guest", "Norm", "Norm", ""));
  }

  @Test
  void evaluatesSafeReceiversOnceAndSkipsArgumentsWhenNull() throws Exception {
    assertOutput(
        "class Receiver { String use(String value) { return value } } "
            + "Receiver? receiver(Integer call) { printLine(call) return null } "
            + "String argument() { printLine(\"argument\") return \"value\" } "
            + "Void main() { printLine(receiver(call: 1)?.use(value: argument()) ?? \"missing\") }",
        String.join(System.lineSeparator(), "1", "missing", ""));
  }

  @Test
  void executesConditionalForWithBreakAndContinue() throws Exception {
    assertOutput(
        "Void main() { Integer value = 0 for value < 6 { value = value + 1 "
            + "if value == 2 { continue } if value == 5 { break } printLine(value) } }",
        String.join(System.lineSeparator(), "1", "3", "4", ""));
  }

  @Test
  void executesResolvedFunctionAndMethodOverloads() throws Exception {
    assertOutput(
        "Integer choose(Integer value) { return value + 1 } "
            + "String choose(String value) { return value + \"!\" } "
            + "class Picker { Integer choose(Integer value) { return value * 2 } "
            + "String choose(String value) { return value + value } } "
            + "Void main() { Picker picker = Picker() printLine(choose(value: 3)) "
            + "printLine(choose(value: \"N\")) printLine(picker.choose(value: 4)) "
            + "printLine(picker.choose(value: \"A\")) }",
        String.join(System.lineSeparator(), "4", "N!", "8", "AA", ""));
  }

  @Test
  void iteratesSteppedRangesInBothDirectionsWithoutOverflow() throws Exception {
    assertOutput(
        "Void main() { Range ascending = range(start: 0, end: 7, step: 2) "
            + "printLine(ascending.size()) for value : ascending { printLine(value) } "
            + "Range descending = range(start: 5, end: -2, step: -3) "
            + "printLine(descending.size()) for value : descending { printLine(value) } "
            + "for value : range(start: 2147483646, end: 2147483647, step: 2) "
            + "{ printLine(value) } }",
        String.join(
            System.lineSeparator(),
            "4",
            "0",
            "2",
            "4",
            "6",
            "3",
            "5",
            "2",
            "-1",
            "2147483646",
            ""));
  }

  @Test
  void rejectsZeroRangeStepAtRuntime() {
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () -> assertOutput("Void main() { range(start: 0, end: 4, step: 0) }", ""));

    assertEquals(RuntimeErrorCode.INVALID_ARGUMENT, exception.code());
  }

  @Test
  void executesFilledLastRemoveLastAndReversedSequenceOperations() throws Exception {
    assertOutput(
        "Void main() { List<Integer> values = List.filled(size: 3, value: 7) values[0] = 1 "
            + "printLine(values.last()) printLine(values.removeLast()) printLine(values.size()) "
            + "List<Integer> reversed = values.reversed() for value : reversed { printLine(value) } "
            + "Array<Boolean> flags = Array.filled(size: 2, value: false) flags[1] = true "
            + "for flag : flags.reversed() { printLine(flag) } }",
        String.join(System.lineSeparator(), "7", "7", "2", "7", "1", "true", "false", ""));
  }

  @Test
  void executesAsciiDigitOperationsAndRejectsNonDigits() throws Exception {
    assertOutput(
        "Void main() { printLine('7'.isAsciiDigit()) printLine('７'.isAsciiDigit()) "
            + "printLine('7'.asciiDigitValue()) }",
        String.join(System.lineSeparator(), "true", "false", "7", ""));
    NormExecutionException exception =
        assertThrows(
            NormExecutionException.class,
            () -> assertOutput("Void main() { 'x'.asciiDigitValue() }", ""));
    assertEquals(RuntimeErrorCode.INVALID_ARGUMENT, exception.code());
  }

  @Test
  void rejectsInvalidFilledSizesAndEmptySequenceTailOperations() {
    NormExecutionException negative =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput(
                    "Void main() { List<Integer> values = List.filled(size: -1, value: 0) }", ""));
    NormExecutionException empty =
        assertThrows(
            NormExecutionException.class,
            () ->
                assertOutput("Void main() { List<Integer> values = List<>() values.last() }", ""));
    assertEquals(RuntimeErrorCode.INVALID_ARGUMENT, negative.code());
    assertEquals(RuntimeErrorCode.EMPTY_COLLECTION, empty.code());
  }

  @TestFactory
  Stream<DynamicTest> runsBasicLanguagePrograms() throws Exception {
    return suite("base");
  }

  @TestFactory
  Stream<DynamicTest> runsSingleFileAlgorithms() throws Exception {
    return suite("algorithms");
  }

  @TestFactory
  Stream<DynamicTest> runsLeetCodeAlgorithms() throws Exception {
    return suite("algorithms/leetcode");
  }

  @TestFactory
  Stream<DynamicTest> runsClassPrograms() throws Exception {
    return suite("class");
  }

  @TestFactory
  Stream<DynamicTest> runsValuePrograms() throws Exception {
    return suite("value");
  }

  @TestFactory
  Stream<DynamicTest> runsGenericPrograms() throws Exception {
    return suite("generics");
  }

  @TestFactory
  Stream<DynamicTest> runsNullablePrograms() throws Exception {
    return suite("nullable");
  }

  @TestFactory
  Stream<DynamicTest> runsOverloadPrograms() throws Exception {
    return suite("overloads");
  }

  @TestFactory
  Stream<DynamicTest> runsInterfacePrograms() throws Exception {
    return suite("interfaces");
  }

  @TestFactory
  Stream<DynamicTest> runsFunctionPrograms() throws Exception {
    return suite("functions");
  }

  @TestFactory
  Stream<DynamicTest> runsExceptionPrograms() throws Exception {
    return suite("exceptions");
  }

  @TestFactory
  Stream<DynamicTest> runsRangePrograms() throws Exception {
    return suite("range");
  }

  @TestFactory
  Stream<DynamicTest> runsMultiFilePrograms() throws Exception {
    return projectSuite("projects");
  }

  private static void assertOutput(String text, String expected) throws Exception {
    assertEquals(expected, dev.w0fv1.norm.testing.NormTestKit.run(text));
  }
}
