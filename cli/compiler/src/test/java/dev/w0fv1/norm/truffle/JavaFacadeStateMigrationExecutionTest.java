package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.*;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.jvm.JavaApplicationTypeName;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.testing.NormTestKit;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaFacadeStateMigrationExecutionTest {
  @TempDir Path directory;

  @Test
  void changedFieldKeepsItsOldFacadeSnapshotWhileCompatibleFieldsStillSynchronize()
      throws Exception {
    String source =
        "public class Counter { public Integer value = 0; public Integer shared = 1 } Void main() {}";
    var old = NormTestKit.compile(source).output().orElseThrow().artifact();
    var next =
        NormTestKit.compile(source.replace("Integer value = 0", "String value = \"zero\""))
            .output()
            .orElseThrow()
            .artifact();
    var record =
        old.program().definitions().stream()
            .filter(
                item ->
                    item.definition() instanceof CoreDefinition.Aggregate declaration
                        && declaration.nominalType().name().equals("Counter"))
            .findFirst()
            .orElseThrow();
    String binary =
        JavaApplicationTypeName.binaryName(
            ((CoreDefinition.Aggregate) record.definition()).nominalType());
    int separator = binary.lastIndexOf('.');
    String packageName = separator < 0 ? "" : "package " + binary.substring(0, separator) + ";";
    String name = binary.substring(separator + 1);
    Path java = directory.resolve(name + ".java");
    Files.writeString(
        java,
        packageName
            + " public class "
            + name
            + " implements dev.w0fv1.norm.bridge.JavaApplicationObject { public int value = 7; public int shared = 1; private transient Object $normState; }");
    Path classes = Files.createDirectories(directory.resolve("classes"));
    String classpath =
        Path.of(
                dev.w0fv1.norm.bridge.JavaApplicationObject.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .toString();
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-cp", classpath, "-d", classes.toString(), java.toString()));
    var oldProgram = new Lowerer(null).lower(old, CoreExecutionPlan.forLiveArtifact(old));
    var newProgram = new Lowerer(null).lower(next, CoreExecutionPlan.forLiveArtifact(next));
    try (var runtime = JvmJarBindingRuntime.prepared(List.of(), List.of(classes), List.of())) {
      var context = ExecutionContext.builder().jarBindingRuntime(runtime).build();
      var oldExecution = oldProgram.execution(context);
      var newExecution = newProgram.execution(context);
      try {
        var oldDispatch =
            new JavaApplicationDispatch(
                oldProgram.program(),
                oldProgram.bridgeTargets(),
                oldProgram.values(),
                oldExecution,
                runtime);
        var newDispatch =
            new JavaApplicationDispatch(
                newProgram.program(),
                newProgram.bridgeTargets(),
                newProgram.values(),
                newExecution,
                runtime);
        var type = oldProgram.program().id(record.id());
        var hostType = runtime.applicationClassLoader().loadClass(binary);
        Object host = hostType.getDeclaredConstructor().newInstance();
        oldDispatch.allocate(type.toString(), host);
        var object = assertInstanceOf(RuntimeValues.ObjectValue.class, oldDispatch.fromJava(host));
        var target = newProgram.annotations().aggregateInfo(type);
        var value = new RuntimeValues.FieldKey(type, "value");
        var shared = new RuntimeValues.FieldKey(type, "shared");
        try (var transaction = RuntimeValues.prepareState(List.of(), null)) {
          var migration = transaction.migrate(object, target);
          migration.write(value, CoreType.STRING, "seven");
          transaction.commit();
        }
        assertSame(object, newDispatch.fromJava(host));
        assertEquals("seven", object.rawField(value, target));
        object.writeRawField(shared, 2, target);
        assertSame(host, newDispatch.toJava(object));
        assertEquals(7, hostType.getField("value").get(host));
        assertEquals(2, hostType.getField("shared").get(host));
        newDispatch.writeField(host, "value", "eight");
        assertEquals(7, hostType.getField("value").get(host));
        hostType.getField("shared").set(host, 3);
        assertSame(object, newDispatch.fromJava(host));
        assertEquals(3, object.rawField(shared, target));
        assertEquals("seven", object.rawField(value, target));
        hostType.getField("value").set(host, 9);
        assertThrows(RuntimeValues.StateMigrationFailure.class, () -> newDispatch.fromJava(host));
      } finally {
        oldExecution.close(null);
        newExecution.close(null);
      }
    }
  }
}
