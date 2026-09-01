package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CommonsLangBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void constructsAndInvokesAJavaObjectFromNorm() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("commons/lang"));
    Path module = moduleRoot.resolve("module.norm");
    Files.writeString(
        module,
        """
        Module module() {
          return module(
            name: "commons.lang",
            version: 1,
            binding: jarBinding(
              target: mavenJar(
                group: "org.apache.commons",
                artifact: "commons-lang3",
                version: "3.20.0"
              ),
              api: [
                jarType(name: "ObjectUtils", members: ["CONST", "firstNonNull", "max"]),
                jarType(name: "StringUtils", members: ["split"]),
                jarType(name: "mutable.MutableInt", members: ["new", "get", "increment"]),
                jarType(name: "mutable.MutableObject", members: ["new", "get", "setValue"])
              ]
            )
          )
        }
        """);
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package commons.lang
        import commons.lang.JavaComparableArray
        import commons.lang.JavaObjectArray
        import commons.lang.JavaStringArray
        import commons.lang.javaComparableArrayNew
        import commons.lang.javaObjectArrayNew
        import commons.lang.javaStringArrayNew
        import commons.lang.objectUtilsCONST
        import commons.lang.objectUtilsFirstNonNull
        import commons.lang.objectUtilsMax
        import commons.lang.stringUtilsSplit
        import commons.lang.mutable.MutableInt
        import commons.lang.mutable.MutableObject
        import commons.lang.mutable.mutableIntNew
        import commons.lang.mutable.mutableObjectNew

        Void main() {
          MutableInt value = mutableIntNew(41)
          value.increment()
          printLine(value.get() ?? 0)
          Integer constant = objectUtilsCONST<Integer>(7) ?? 0
          MutableObject<String?> text = mutableObjectNew<String>("Norm")
          text.setValue("Java")
          printLine(text.get() ?? "")
          printLine(constant)
          JavaStringArray parts = stringUtilsSplit("Norm Java") ?? javaStringArrayNew(0)
          printLine(parts.size())
          printLine(parts.get(0) ?? "")
          parts.set(index: 0, value: "NAR")
          printLine(parts.get(0) ?? "")
          JavaObjectArray<String> choices = javaObjectArrayNew<String>(2)
          choices.set(index: 0, value: "first")
          choices.set(index: 1, value: "second")
          printLine(objectUtilsFirstNonNull<String>(choices) ?? "")
          JavaComparableArray<String> ordered = javaComparableArrayNew<String>(2)
          ordered.set(index: 0, value: "first")
          ordered.set(index: 1, value: "second")
          printLine(objectUtilsMax<String>(ordered) ?? "")
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("maven-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      new ModuleBindingResolutionService(projects).resolve(module);
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        "42"
            + System.lineSeparator()
            + "Java"
            + System.lineSeparator()
            + "7"
            + System.lineSeparator()
            + "2"
            + System.lineSeparator()
            + "Norm"
            + System.lineSeparator()
            + "NAR"
            + System.lineSeparator()
            + "first"
            + System.lineSeparator()
            + "second"
            + System.lineSeparator(),
        output.toString());
  }
}
