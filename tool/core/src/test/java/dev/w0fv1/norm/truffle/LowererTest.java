package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.NodeUtil;
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
        new Lowerer(null, new PrintWriter(new StringWriter())).lower(checked);
    var root = executable.entryPoint().getRootNode();

    assertInstanceOf(FunctionRootNode.class, root);
    assertFalse(NodeUtil.findAllNodeInstances(root, DirectCallNode.class).isEmpty());
    assertFalse(NodeUtil.findAllNodeInstances(root, LoopNode.class).isEmpty());
  }
}
