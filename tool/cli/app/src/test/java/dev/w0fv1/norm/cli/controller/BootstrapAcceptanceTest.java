package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.cli.value.ExitCode;
import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.utils.BackendInfo;
import dev.w0fv1.norm.value.LanguageMetadata;
import dev.w0fv1.norm.value.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class BootstrapAcceptanceTest {
  @Test
  void allM0ModulesAreUsableTogether() {
    SourceFile source = SourceFile.of(Path.of("hello.norm"), "Void main() {}\n");
    var output = new StringWriter();

    int exitCode =
        new CliController()
            .run(
                new String[] {"--version"},
                new PrintWriter(output),
                new PrintWriter(new StringWriter()));

    assertTrue(source.lineText(1).contains("main"));
    assertEquals("norm", LanguageMetadata.ID);
    assertFalse(BackendInfo.runtimeName().isBlank());
    assertEquals(ExitCode.SUCCESS, exitCode);
    assertFalse(output.toString().isBlank());
  }

  @Test
  void compilesAndExecutesHelloWorldAcrossTheModuleBoundary() {
    SourceFile source =
        SourceFile.of(Path.of("hello.norm"), "Void main() { printLine(\"Hello from Norm\") }\n");
    var compilation = new Compiler().compile(source);
    var output = new StringWriter();

    new ProgramRunner().run(compilation.program().orElseThrow(), new PrintWriter(output));

    assertEquals("Hello from Norm" + System.lineSeparator(), output.toString());
  }
}
