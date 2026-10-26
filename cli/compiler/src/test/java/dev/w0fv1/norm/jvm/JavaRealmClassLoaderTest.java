package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.net.URL;
import org.junit.jupiter.api.Test;

final class JavaRealmClassLoaderTest {
  @Test
  void allRealmClassLoadersShareOneLoadingLock() throws Exception {
    try (var first = new ExposedLoader();
        var second = new ExposedLoader()) {
      Object firstLock = first.lock("fixture.First");
      assertSame(firstLock, first.lock("fixture.Second"));
      assertSame(firstLock, second.lock("fixture.Third"));
    }
  }

  private static final class ExposedLoader extends JavaRealmClassLoader {
    private ExposedLoader() {
      super(new URL[0], ClassLoader.getPlatformClassLoader());
    }

    private Object lock(String name) {
      return getClassLoadingLock(name);
    }
  }
}
