package dev.w0fv1.norm.pyrun;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class PythonRuntimeService {
  private final PythonRuntimeCatalog catalog;
  private final Path runtimeCache;

  public PythonRuntimeService() throws IOException {
    this(PythonRuntimeCatalog.current(), defaultCache());
  }

  PythonRuntimeService(PythonRuntimeCatalog catalog, Path runtimeCache) {
    this.catalog = catalog;
    this.runtimeCache = runtimeCache.toAbsolutePath().normalize();
  }

  public Path prepare(Path project, Path requirements, Consumer<String> progress)
      throws IOException {
    PythonRuntimePlatform platform = PythonRuntimePlatform.current();
    PythonRuntimeDistribution distribution = catalog.distribution(platform);
    Path interpreter =
        new PythonRuntimeInstaller(distribution, runtimeCache, PythonRuntimeInstaller::downloadHttp)
            .install(progress);
    String requirementsHash = Sha256Digest.compute(requirements).value();
    Path environment =
        project
            .resolve(".norm")
            .resolve("python")
            .resolve(catalog.version())
            .resolve(platform.key())
            .resolve(requirementsHash);
    return new PythonEnvironmentManager(platform)
        .ensure(interpreter, environment, requirements, progress);
  }

  public String version() {
    return catalog.version();
  }

  private static Path defaultCache() {
    String configured = System.getProperty("norm.pyrun.home");
    if (configured == null || configured.isBlank()) configured = System.getenv("NORM_PYRUN_HOME");
    return configured == null || configured.isBlank()
        ? Path.of(System.getProperty("user.home"), ".norm", "pyrun", "runtimes")
        : Path.of(configured).toAbsolutePath().normalize();
  }
}
