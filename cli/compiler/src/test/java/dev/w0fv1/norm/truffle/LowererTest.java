package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.source.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class LowererTest {
  @Test
  void lowersFunctionsCallsAndLoopsToExecutableNodes() {
    var source =
        SourceFile.of(
            Path.of("lowering.norm"),
            "Integer add(Integer left, Integer right) { return left + right } "
                + "Void main() { Integer total = 0 for value : range(start: 0, end: 3) { "
                + "total = add(left: total, right: value) } printLine(total) }");
    var checked = new CompilerSession().compile(source).output().orElseThrow();

    ExecutableProgram executable = new Lowerer(null).lower(checked.artifact());
    var root = executable.entryPoint().getRootNode();

    assertInstanceOf(FunctionRootNode.class, root);
    assertFalse(NodeUtil.findAllNodeInstances(root, DirectCallNode.class).isEmpty());
    assertFalse(NodeUtil.findAllNodeInstances(root, LoopNode.class).isEmpty());
  }

  @Test
  void substitutesRuntimeTypesConstructedInsideGenericFunctions() {
    var source =
        SourceFile.of(
            Path.of("runtime-generics.norm"),
            "class Box<T> {} Box<T> create<T>() { return Box<T>() } "
                + "Box<Integer> probe() { return create<Integer>() } Void main() {}");
    var checked = new CompilerSession().compile(source).output().orElseThrow();
    var probe = checked.artifact().namespace().occurrence("", "probe").orElseThrow();

    ExecutableProgram executable =
        new Lowerer(null).lower(checked.artifact().withEntryPoint(probe));
    RuntimeValues.ObjectValue result =
        assertInstanceOf(
            RuntimeValues.ObjectValue.class,
            executable.execute(ExecutionContext.of(new PrintWriter(new StringWriter()))));

    CoreType.Declared type = assertInstanceOf(CoreType.Declared.class, result.type());
    assertInstanceOf(CoreTypeConstructor.User.class, type.constructor());
    assertEquals(java.util.List.of(CoreType.INTEGER), type.arguments());
  }

  @Test
  void lowersNestedVariantPatternsWithSourceTypes() {
    var source =
        SourceFile.of(
            Path.of("nested-patterns.norm"),
            "enum Delivery { Pending, Sent(String code) } "
                + "enum Event { Updated(Delivery delivery), Ignored } "
                + "String describe(Event event) { return switch event { "
                + "case Updated(Sent(String code)) { break code } "
                + "case Updated(_) { break \"waiting\" } "
                + "case Ignored { break \"ignored\" } } } "
                + "Void main() { "
                + "printLine(describe(event: Event.Updated(delivery: Delivery.Sent(code: \"N-42\")))) "
                + "printLine(describe(event: Event.Updated(delivery: Delivery.Pending))) } ");
    var checked = new CompilerSession().compile(source).output().orElseThrow();
    var output = new StringWriter();

    new Lowerer(null)
        .lower(checked.artifact())
        .execute(ExecutionContext.of(new PrintWriter(output)));

    assertEquals("N-42\nwaiting\n", output.toString().replace("\r\n", "\n"));
  }
}
