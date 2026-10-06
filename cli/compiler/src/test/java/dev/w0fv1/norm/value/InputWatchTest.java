package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

final class InputWatchTest {
  @Test
  void escapesLiteralBracketAndBraceNames(@TempDir Path directory) {
    var watch = InputWatch.file(directory.resolve("binding[1]{a,b}.jar"));
    assertEquals("binding[[]1[]][{]a,b[}].jar", watch.pattern());
    assertEquals(
        directory.toAbsolutePath().toString().replace('\\', '/') + "/" + watch.pattern(),
        watch.absolutePattern());
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void escapesLiteralWildcardNames(@TempDir Path directory) {
    assertEquals(
        "binding[*][?].jar", InputWatch.file(directory.resolve("binding*?.jar")).pattern());
  }
}
