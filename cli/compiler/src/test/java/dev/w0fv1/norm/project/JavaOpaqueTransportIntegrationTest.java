package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class JavaOpaqueTransportIntegrationTest {
  @TempDir Path temporaryDirectory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesNormObjectIdentityThroughJavaMapsObjectsAndGenericCallbacks(boolean exportedEnum)
      throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("sample"));
    Path jar = root.resolve("transport.jar");
    var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC, "sample/TransportApi", null, "java/lang/Object", null);
    var identity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "object",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            null);
    identity.visitCode();
    identity.visitVarInsn(Opcodes.ALOAD, 0);
    identity.visitInsn(Opcodes.ARETURN);
    identity.visitMaxs(0, 0);
    identity.visitEnd();
    var callback =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "callback",
            "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;",
            "<T:Ljava/lang/Object;>(TT;Ljava/util/function/Function<TT;TT;>;)TT;",
            null);
    callback.visitCode();
    callback.visitVarInsn(Opcodes.ALOAD, 1);
    callback.visitVarInsn(Opcodes.ALOAD, 0);
    callback.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "java/util/function/Function",
        "apply",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        true);
    callback.visitInsn(Opcodes.ARETURN);
    callback.visitMaxs(0, 0);
    callback.visitEnd();
    writer.visitEnd();
    try (var archive = new JarOutputStream(Files.newOutputStream(jar))) {
      archive.putNextEntry(new JarEntry("sample/TransportApi.class"));
      archive.write(writer.toByteArray());
      archive.closeEntry();
    }
    Files.writeString(
        root.resolve("module.norm"),
        """
        Module module() { return module(name: "sample", version: 1,
          binding: jarBinding(target: localJar(path: "transport.jar", integrity: sha256("%s")),
            api: [jarType(name: "TransportApi", members: ["object", "callback"])])) }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry =
        Files.writeString(
            root.resolve("Main.norm"),
            """
        package sample
        import java.base.util.linkedHashMapNew
        enum Tone { Primary, Muted }
        class Reflected {}
        class Payload {
          public Integer code = 7
          Payload() {}
        }
        Void main() {
          %s
          var tones = linkedHashMapNew<String, Tone>()
          tones.put(arg0: "tone", arg1: Tone.Primary)
          require(condition: tones.get(arg0: "tone") == Tone.Primary,
            message: "Java map replaced the native enum")
          require(condition: transportApiObject(Tone.Primary) == Tone.Primary,
            message: "Java Object transport replaced the native enum")
          var tokens = linkedHashMapNew<String, Class<Reflected>>()
          tokens.put(arg0: "type", arg1: Reflected.class)
          require(condition: tokens.get(arg0: "type") == Reflected.class,
            message: "Java map replaced the class token")
          require(condition: transportApiObject(Reflected.class) == Reflected.class,
            message: "Java Object transport replaced the class token")
          var payload = Payload()
          var map = linkedHashMapNew<String, Payload>()
          map.put(arg0: "key", arg1: payload)
          var mapped = map.get(arg0: "key")!!
          require(condition: mapped == payload, message: "Java map replaced the Norm object")
          printLine(mapped.code)
          require(condition: transportApiObject(payload) == payload,
            message: "Java Object transport replaced the Norm object")
          Function<Payload?(Payload?)> identity = (value) {
            require(condition: value == payload, message: "Java callback replaced its argument")
            return value
          }
          var returned = transportApiCallback<Payload>(arg0: payload, arg1: identity)!!
          require(condition: returned == payload, message: "Java callback replaced its result")
          printLine(returned.code)
        }
        """
                .formatted(
                    exportedEnum
                        ? "require(condition: transportApiObject(Tone.class) == Tone.class, message: \"Exported enum class token changed\")"
                        : ""));
    var backend = new NormRuntime();
    var output = new StringWriter();
    try (var environment = ProjectEnvironment.bootstrap(backend);
        var projects = environment.projectLoader(temporaryDirectory.resolve("cache"));
        var runner = new ApplicationRunner(projects, environment.compilerSession(), backend)) {
      var result = runner.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("7" + System.lineSeparator() + "7" + System.lineSeparator(), output.toString());
  }
}
