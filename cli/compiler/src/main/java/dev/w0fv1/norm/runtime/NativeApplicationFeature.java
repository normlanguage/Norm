package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.jvm.JavaCallbackTypes;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;

public final class NativeApplicationFeature implements Feature {
  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    String archive = System.getProperty("norm.native.application.archive");
    if (archive == null)
      throw new IllegalStateException("Native application archive is unavailable");
    try {
      NativeApplicationData application =
          NativeApplicationArchive.read(java.nio.file.Path.of(archive));
      Class<?> registryType =
          access.findClassByName(dev.w0fv1.norm.jvm.JavaDirectCallBundle.REGISTRY_NAME);
      if (registryType == null)
        throw new IllegalStateException("Native direct Java calls are absent");
      var registry =
          (dev.w0fv1.norm.bridge.JavaDirectCallRegistry)
              registryType.getConstructor().newInstance();
      NativeApplicationMain.install(
          application,
          new dev.w0fv1.norm.truffle.TruffleExecutionBackend()
              .prepare(application.artifact(), application.execution()),
          registry.calls(),
          dev.w0fv1.norm.jvm.LinkedJavaClasses.resolve(
              application.bindings(), access.getApplicationClassLoader()),
          dev.w0fv1.norm.jvm.JavaApplicationCallLinker.link(access.getApplicationClassLoader()));
      for (String callback : JavaCallbackTypes.from(application.bindings())) {
        Class<?> type = access.findClassByName(callback);
        if (type == null)
          throw new IllegalStateException("Java callback type is unavailable: " + callback);
        RuntimeProxyCreation.register(type);
      }
    } catch (java.io.IOException | ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot read Native Image application", exception);
    }
  }
}
