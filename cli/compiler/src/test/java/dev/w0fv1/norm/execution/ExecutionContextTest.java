package dev.w0fv1.norm.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.w0fv1.norm.platform.PlatformInstant;
import dev.w0fv1.norm.platform.SystemPlatform;
import dev.w0fv1.norm.platform.file.FileSystem;
import dev.w0fv1.norm.platform.http.HttpTransport;
import dev.w0fv1.norm.platform.time.SystemClock;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class ExecutionContextTest {
  @Test
  void preservesTheHostApplicationPackageAcrossContextDerivations() {
    ExecutionContext context =
        ExecutionContext.builder()
            .applicationPackage("norm.generated.application")
            .build()
            .withJarBindingRuntime(JarBindingRuntime.unavailable());

    assertEquals("norm.generated.application", context.applicationPackage());
  }

  @Test
  void carriesTheExplicitSystemPlatform() {
    FileSystem fileSystem = SystemPlatform.unavailable().fileSystem();
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

          @Override
          public HttpTransport httpTransport() {
            return SystemPlatform.unavailable().httpTransport();
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
