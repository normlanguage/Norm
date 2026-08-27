package dev.w0fv1.norm.execution;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class ExecutionContextTest {
  @Test
  void carriesTheExplicitSystemPlatform() {
    FileSystem fileSystem = (path, encoding) -> "content";
    SystemClock clock = () -> new PlatformInstant(10, 20);
    SystemPlatform platform =
        new SystemPlatform() {
          @Override
          public FileSystem fileSystem() {
            return fileSystem;
          }

          @Override
          public SystemClock clock() {
            return clock;
          }
        };

    ExecutionContext context =
        ExecutionContext.builder()
            .output(new PrintWriter(new StringWriter()))
            .platform(platform)
            .build();

    assertSame(platform, context.platform());
    assertSame(fileSystem, context.platform().fileSystem());
    assertSame(clock, context.platform().clock());
  }
}
