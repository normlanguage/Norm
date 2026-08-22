package dev.w0fv1.norm.truffle;

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
            "int add(int left, int right) { return left + right } "
                + "void main() { int total = 0 for value : range(start: 0, end: 3) { "
                + "total = add(left: total, right: value) } print(total) }");
    var checked = new Compiler().compile(source).program().orElseThrow();

    ExecutableProgram executable =
        new Lowerer(null, ExecutionContext.of(new PrintWriter(new StringWriter())))
            .lower(checked.boundProgram());
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
                + "Box<int> probe() { return create<int>() } void main() {}");
    var checked = new Compiler().compile(source).program().orElseThrow();
    var probe =
        checked.boundProgram().callables().stream()
            .filter(function -> function.name().equals("probe"))
            .findFirst()
            .orElseThrow();

    ExecutableProgram executable =
        new Lowerer(null, ExecutionContext.of(new PrintWriter(new StringWriter())))
            .lower(checked.boundProgram().withEntryPoint(probe.id()));
    RuntimeValues.ObjectValue result =
        assertInstanceOf(RuntimeValues.ObjectValue.class, executable.entryPoint().call());

    org.junit.jupiter.api.Assertions.assertEquals("Box<int>", result.type.displayName());
  }
}
