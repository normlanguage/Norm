package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.SourceFile;
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
    var checked = new Compiler().compile(source).program().orElseThrow();

    ExecutableProgram executable = new Lowerer(null).lower(checked.coreCompilation());
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
    var checked = new Compiler().compile(source).program().orElseThrow();
    var probe = checked.coreCompilation().namespace().occurrence("", "probe").orElseThrow();

    ExecutableProgram executable =
        new Lowerer(null).lower(checked.coreCompilation().withEntryPoint(probe));
    RuntimeValues.ObjectValue result =
        assertInstanceOf(
            RuntimeValues.ObjectValue.class,
            executable.entryPoint().call(ExecutionContext.of(new PrintWriter(new StringWriter()))));

    dev.w0fv1.norm.core.CoreType.Declared type =
        assertInstanceOf(dev.w0fv1.norm.core.CoreType.Declared.class, result.type);
    assertInstanceOf(dev.w0fv1.norm.core.CoreTypeConstructor.User.class, type.constructor());
    assertEquals(java.util.List.of(dev.w0fv1.norm.core.CoreType.INTEGER), type.arguments());
  }
}
