package dev.w0fv1.norm.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JavaApplicationBridgeTest {
  @Test
  void retiringPreviousRegistrationDoesNotRemoveItsPublishedReplacement() throws Exception {
    try (var loader = new URLClassLoader(new URL[0], getClass().getClassLoader())) {
      var previous =
          (JavaApplicationBridge.Handler)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {JavaApplicationBridge.Handler.class},
                  (proxy, method, arguments) -> "old");
      var candidate =
          (JavaApplicationBridge.Handler)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {JavaApplicationBridge.Handler.class},
                  (proxy, method, arguments) -> "new");
      try (var oldRegistration = JavaApplicationBridge.install(loader, previous);
          var newRegistration = JavaApplicationBridge.replace(loader, candidate)) {
        oldRegistration.close();
        assertEquals("new", JavaApplicationBridge.toJava(loader, new Object()));
        newRegistration.close();
        assertThrows(
            IllegalStateException.class, () -> JavaApplicationBridge.toJava(loader, new Object()));
      }
    }
  }

  @Test
  void bridgeRegistryDoesNotKeepUnreachableDependencyRealmsAlive() throws Exception {
    var realm = new AtomicReference<WeakReference<ClassLoader>>();
    var callback = new AtomicReference<WeakReference<JavaApplicationBridge.Handler>>();
    var failure = new AtomicReference<Throwable>();
    var creator =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    ClassLoader loader =
                        new URLClassLoader(new URL[0], getClass().getClassLoader());
                    JavaApplicationBridge.Handler handler =
                        (JavaApplicationBridge.Handler)
                            Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {JavaApplicationBridge.Handler.class},
                                (proxy, method, arguments) ->
                                    arguments == null ? null : arguments[0]);
                    var registration = JavaApplicationBridge.install(loader, handler);
                    Object value = new Object();
                    assertSame(value, JavaApplicationBridge.toJava(loader, value));
                    realm.set(new WeakReference<>(loader));
                    callback.set(new WeakReference<>(handler));
                    java.lang.ref.Reference.reachabilityFence(registration);
                  } catch (Throwable exception) {
                    failure.set(exception);
                  }
                });
    creator.join();
    if (failure.get() != null) throw new AssertionError(failure.get());
    assertTimeoutPreemptively(
        Duration.ofSeconds(15),
        () -> {
          while (realm.get().get() != null || callback.get().get() != null) {
            System.gc();
            Thread.sleep(50);
          }
        });
  }
}
