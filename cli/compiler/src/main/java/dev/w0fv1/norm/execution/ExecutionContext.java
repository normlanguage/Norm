package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.platform.SystemPlatform;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

public final class ExecutionContext {
  private final InputStream input;
  private final PrintWriter error;
  private final Map<String, String> environment;
  private final java.nio.file.Path workingDirectory;
  private final AtomicInteger exitCode;
  private final PrintWriter output;
  private final PrintWriter expectedOutput;
  private final List<String> arguments;
  private final BooleanSupplier cancellation;
  private final Optional<ModulePublisher> modulePublisher;
  private final Optional<JavaApplicationEntrypoint> javaApplicationEntrypoint;
  private final String applicationPackage;
  private final Optional<java.nio.file.Path> applicationDirectory;
  private final JarBindingRuntime jarBindingRuntime;
  private final SystemPlatform platform;

  private ExecutionContext(Builder builder) {
    input = Objects.requireNonNull(builder.input, "input");
    error = Objects.requireNonNull(builder.error, "error");
    environment = Map.copyOf(builder.environment);
    workingDirectory = builder.workingDirectory;
    exitCode = builder.exitCode;
    output = Objects.requireNonNull(builder.output, "output");
    expectedOutput = Objects.requireNonNull(builder.expectedOutput, "expectedOutput");
    arguments = List.copyOf(builder.arguments);
    cancellation = Objects.requireNonNull(builder.cancellation, "cancellation");
    modulePublisher = Optional.ofNullable(builder.modulePublisher);
    javaApplicationEntrypoint = Optional.ofNullable(builder.javaApplicationEntrypoint);
    applicationPackage = Objects.requireNonNull(builder.applicationPackage, "applicationPackage");
    applicationDirectory = Optional.ofNullable(builder.applicationDirectory);
    jarBindingRuntime = Objects.requireNonNull(builder.jarBindingRuntime, "jarBindingRuntime");
    platform = Objects.requireNonNull(builder.platform, "platform");
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ExecutionContext of(PrintWriter output) {
    return builder().output(output).build();
  }

  public static ExecutionContext of(PrintWriter output, SystemPlatform platform) {
    return builder().output(output).platform(platform).build();
  }

  public static ExecutionContext testing(PrintWriter output, PrintWriter expectedOutput) {
    return builder().output(output).expectedOutput(expectedOutput).build();
  }

  public static ExecutionContext testing(
      PrintWriter output, PrintWriter expectedOutput, SystemPlatform platform) {
    return builder().output(output).expectedOutput(expectedOutput).platform(platform).build();
  }

  public static ExecutionContext module(ModulePublisher publisher) {
    return builder().modulePublisher(publisher).build();
  }

  public InputStream input() {
    return input;
  }

  public PrintWriter error() {
    return error;
  }

  public Map<String, String> environment() {
    return environment;
  }

  public java.nio.file.Path workingDirectory() {
    return workingDirectory;
  }

  public int exitCode() {
    return exitCode.get();
  }

  public void setExitCode(int value) {
    if (value < 0 || value > 255) throw new IllegalArgumentException("exit code must be in 0..255");
    exitCode.set(value);
  }

  public PrintWriter output() {
    return output;
  }

  public PrintWriter expectedOutput() {
    return expectedOutput;
  }

  public List<String> arguments() {
    return arguments;
  }

  public BooleanSupplier cancellation() {
    return cancellation;
  }

  public Optional<ModulePublisher> modulePublisher() {
    return modulePublisher;
  }

  public SystemPlatform platform() {
    return platform;
  }

  public Optional<JavaApplicationEntrypoint> javaApplicationEntrypoint() {
    return javaApplicationEntrypoint;
  }

  public String applicationPackage() {
    return applicationPackage;
  }

  public Optional<java.nio.file.Path> applicationDirectory() {
    return applicationDirectory;
  }

  public ExecutionContext withApplicationDirectory(java.nio.file.Path value) {
    return new Builder(this).applicationDirectory(value).build();
  }

  public ExecutionContext withOutput(PrintWriter output, PrintWriter expectedOutput) {
    return new Builder(this).output(output).expectedOutput(expectedOutput).build();
  }

  public ExecutionContext withWorkingDirectory(java.nio.file.Path directory) {
    return new Builder(this)
        .workingDirectory(directory)
        .platform(new dev.w0fv1.norm.platform.WorkingDirectoryPlatform(platform, directory))
        .build();
  }

  public JarBindingRuntime jarBindingRuntime() {
    return jarBindingRuntime;
  }

  public ExecutionContext withJarBindingRuntime(JarBindingRuntime value) {
    return new Builder(this).jarBindingRuntime(value).build();
  }

  public ExecutionContext withJavaApplicationEntrypoint(JavaApplicationEntrypoint value) {
    return new Builder(this).javaApplicationEntrypoint(value).build();
  }

  public ExecutionContext withApplicationPackage(String value) {
    return new Builder(this).applicationPackage(value).build();
  }

  public static final class Builder {
    private InputStream input = InputStream.nullInputStream();
    private PrintWriter error = new PrintWriter(Writer.nullWriter());
    private Map<String, String> environment = Map.of();
    private java.nio.file.Path workingDirectory =
        java.nio.file.Path.of("").toAbsolutePath().normalize();
    private AtomicInteger exitCode = new AtomicInteger();
    private PrintWriter output = new PrintWriter(Writer.nullWriter());
    private PrintWriter expectedOutput = new PrintWriter(Writer.nullWriter());
    private List<String> arguments = List.of();
    private BooleanSupplier cancellation = () -> false;
    private ModulePublisher modulePublisher;
    private JavaApplicationEntrypoint javaApplicationEntrypoint;
    private String applicationPackage = "";
    private java.nio.file.Path applicationDirectory;
    private JarBindingRuntime jarBindingRuntime = JarBindingRuntime.unavailable();
    private SystemPlatform platform = SystemPlatform.unavailable();

    private Builder() {}

    private Builder(ExecutionContext context) {
      input = context.input;
      error = context.error;
      environment = context.environment;
      workingDirectory = context.workingDirectory;
      exitCode = context.exitCode;
      output = context.output;
      expectedOutput = context.expectedOutput;
      arguments = context.arguments;
      cancellation = context.cancellation;
      modulePublisher = context.modulePublisher.orElse(null);
      javaApplicationEntrypoint = context.javaApplicationEntrypoint.orElse(null);
      applicationPackage = context.applicationPackage;
      applicationDirectory = context.applicationDirectory.orElse(null);
      jarBindingRuntime = context.jarBindingRuntime;
      platform = context.platform;
    }

    public Builder input(InputStream value) {
      input = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder error(PrintWriter value) {
      error = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder environment(Map<String, String> value) {
      environment = Map.copyOf(value);
      return this;
    }

    public Builder workingDirectory(java.nio.file.Path value) {
      workingDirectory = value.toAbsolutePath().normalize();
      return this;
    }

    public Builder applicationDirectory(java.nio.file.Path value) {
      applicationDirectory = Objects.requireNonNull(value, "value").toAbsolutePath().normalize();
      return this;
    }

    public Builder output(PrintWriter value) {
      output = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder expectedOutput(PrintWriter value) {
      expectedOutput = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder arguments(List<String> value) {
      arguments = List.copyOf(value);
      return this;
    }

    public Builder cancellation(BooleanSupplier value) {
      cancellation = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder modulePublisher(ModulePublisher value) {
      modulePublisher = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder javaApplicationEntrypoint(JavaApplicationEntrypoint value) {
      javaApplicationEntrypoint = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder applicationPackage(String value) {
      applicationPackage = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder jarBindingRuntime(JarBindingRuntime value) {
      jarBindingRuntime = Objects.requireNonNull(value, "value");
      return this;
    }

    public Builder platform(SystemPlatform value) {
      platform = Objects.requireNonNull(value, "value");
      return this;
    }

    public ExecutionContext build() {
      return new ExecutionContext(this);
    }
  }
}
