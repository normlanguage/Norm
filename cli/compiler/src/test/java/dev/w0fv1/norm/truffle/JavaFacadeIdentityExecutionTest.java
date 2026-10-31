package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.jvm.JavaApplicationTypeName;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.testing.NormTestKit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaFacadeIdentityExecutionTest {
  @TempDir Path directory;

  @Test
  void oldFacadeRoundtripPreservesItsClassificationWhileRealSubclassesDispatchToJava()
      throws Exception {
    String source = "public class Counter { public Integer value = 0 } Void main() {}";
    var artifact = NormTestKit.compile(source).output().orElseThrow().artifact();
    var revised =
        NormTestKit.compile(source.replace("value = 0", "value = 0; public Integer extra = 1"))
            .output()
            .orElseThrow()
            .artifact();
    var aggregate =
        artifact.program().definitions().stream()
            .filter(
                record ->
                    record.definition() instanceof CoreDefinition.Aggregate declaration
                        && declaration.nominalType().name().equals("Counter"))
            .findFirst()
            .orElseThrow();
    String binary =
        JavaApplicationTypeName.binaryName(
            ((CoreDefinition.Aggregate) aggregate.definition()).nominalType());
    int separator = binary.lastIndexOf('.');
    String packageName = separator < 0 ? "" : "package " + binary.substring(0, separator) + ";";
    String name = binary.substring(separator + 1);
    String facade =
        packageName
            + " public class "
            + name
            + " implements dev.w0fv1.norm.bridge.JavaApplicationObject { public int value = 7; private transient Object $normState; %s public static class Child extends "
            + name
            + " {} }";
    Path javaSource = directory.resolve(name + ".java");
    Path oldClasses = Files.createDirectories(directory.resolve("old"));
    Path newClasses = Files.createDirectories(directory.resolve("new"));
    String classpath =
        Path.of(
                dev.w0fv1.norm.bridge.JavaApplicationObject.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
            .toString();
    Files.writeString(javaSource, facade.formatted(""));
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-cp",
                classpath,
                "-d",
                oldClasses.toString(),
                javaSource.toString()));
    Files.writeString(javaSource, facade.formatted("public int extra = 1;"));
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-cp",
                classpath,
                "-d",
                newClasses.toString(),
                javaSource.toString()));
    var oldProgram = new Lowerer(null).lower(artifact, CoreExecutionPlan.forLiveArtifact(artifact));
    var newProgram = new Lowerer(null).lower(revised, CoreExecutionPlan.forLiveArtifact(revised));
    try (var oldRuntime = JvmJarBindingRuntime.prepared(List.of(), List.of(oldClasses), List.of());
        var newRuntime = JvmJarBindingRuntime.prepared(List.of(), List.of(newClasses), List.of())) {
      var oldState =
          oldProgram.execution(ExecutionContext.builder().jarBindingRuntime(oldRuntime).build());
      var newState =
          newProgram.execution(ExecutionContext.builder().jarBindingRuntime(newRuntime).build());
      try {
        var oldDispatch =
            new JavaApplicationDispatch(
                oldProgram.program(),
                oldProgram.bridgeTargets(),
                oldProgram.values(),
                oldState,
                oldRuntime);
        var newDispatch =
            new JavaApplicationDispatch(
                newProgram.program(),
                newProgram.bridgeTargets(),
                newProgram.values(),
                newState,
                newRuntime);
        Class<?> oldType = oldRuntime.applicationClassLoader().loadClass(binary);
        assertNotSame(oldType, newRuntime.applicationClassLoader().loadClass(binary));
        Object host = oldType.getDeclaredConstructor().newInstance();
        String type = oldProgram.program().id(aggregate.id()).toString();
        oldDispatch.allocate(type, host);
        var guest = assertInstanceOf(RuntimeValues.ObjectValue.class, oldDispatch.fromJava(host));
        assertFalse(guest.dispatchToHost);
        assertSame(guest, newDispatch.fromJava(host));
        assertFalse(guest.dispatchToHost);
        Object subclass =
            oldRuntime
                .applicationClassLoader()
                .loadClass(binary + "$Child")
                .getDeclaredConstructor()
                .newInstance();
        oldDispatch.allocate(type, subclass);
        var child =
            assertInstanceOf(RuntimeValues.ObjectValue.class, oldDispatch.fromJava(subclass));
        assertTrue(child.dispatchToHost);
        assertSame(child, newDispatch.fromJava(subclass));
        assertTrue(child.dispatchToHost);
      } finally {
        oldState.close(null);
        newState.close(null);
      }
    }
  }
}
