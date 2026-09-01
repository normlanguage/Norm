package dev.w0fv1.norm.project;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

final class JUnitPlatformTestRunner {
  ProjectTestReport run(ClassLoader applicationClassLoader, List<String> binaryNames) {
    Objects.requireNonNull(applicationClassLoader, "applicationClassLoader");
    List<Class<?>> classes = new ArrayList<>();
    for (String binaryName : new TreeSet<>(binaryNames)) {
      classes.add(load(applicationClassLoader, binaryName));
    }
    if (classes.isEmpty()) return new ProjectTestReport(0, 0, 0, 0, 0, List.of());
    var discovery =
        request().selectors(classes.stream().map(type -> selectClass(type)).toList()).build();
    var listener = new SummaryGeneratingListener();
    Launcher launcher = LauncherFactory.create();
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(applicationClassLoader);
      launcher.execute(discovery, listener);
    } finally {
      thread.setContextClassLoader(previous);
    }
    var summary = listener.getSummary();
    List<ProjectTestFailure> failures = new ArrayList<>();
    summary
        .getFailures()
        .forEach(
            failure -> {
              Throwable exception = failure.getException();
              String message = exception.getMessage();
              if (message == null || message.isBlank()) message = exception.getClass().getName();
              failures.add(
                  new ProjectTestFailure(failure.getTestIdentifier().getDisplayName(), message));
            });
    return new ProjectTestReport(
        summary.getTestsFoundCount(),
        summary.getTestsSucceededCount(),
        summary.getTestsFailedCount(),
        summary.getTestsSkippedCount(),
        summary.getContainersFailedCount(),
        failures);
  }

  private static Class<?> load(ClassLoader loader, String binaryName) {
    try {
      return Class.forName(binaryName, false, loader);
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(
          "generated Norm test class is absent: " + binaryName, exception);
    }
  }
}
