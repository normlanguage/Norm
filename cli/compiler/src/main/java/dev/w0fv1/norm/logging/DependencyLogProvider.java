package dev.w0fv1.norm.logging;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

public final class DependencyLogProvider implements SLF4JServiceProvider {
  private final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();
  private final IMarkerFactory markers = new BasicMarkerFactory();
  private final MDCAdapter mdc = new BasicMDCAdapter();

  public ILoggerFactory getLoggerFactory() {
    return name -> loggers.computeIfAbsent(name, DependencyLogger::new);
  }

  public IMarkerFactory getMarkerFactory() {
    return markers;
  }

  public MDCAdapter getMDCAdapter() {
    return mdc;
  }

  public String getRequestedApiVersion() {
    return "2.0.99";
  }

  public void initialize() {}

  private static final class DependencyLogger extends LegacyAbstractLogger {
    private static final long serialVersionUID = 1L;

    DependencyLogger(String name) {
      this.name = name;
    }

    public boolean isTraceEnabled() {
      return DependencyLogs.enabled(Level.TRACE);
    }

    public boolean isDebugEnabled() {
      return DependencyLogs.enabled(Level.DEBUG);
    }

    public boolean isInfoEnabled() {
      return DependencyLogs.enabled(Level.INFO);
    }

    public boolean isWarnEnabled() {
      return DependencyLogs.enabled(Level.WARN);
    }

    public boolean isErrorEnabled() {
      return DependencyLogs.enabled(Level.ERROR);
    }

    protected String getFullyQualifiedCallerName() {
      return getClass().getName();
    }

    protected void handleNormalizedLoggingCall(
        Level level, Marker marker, String pattern, Object[] arguments, Throwable cause) {
      DependencyLogs.write(
          level, name, MessageFormatter.basicArrayFormat(pattern, arguments), cause);
    }
  }
}
