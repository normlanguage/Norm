package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.platform.SystemPlatform;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class ExecutionContext {
  private final Reader input;
  private final PrintWriter output;
  private final PrintWriter expectedOutput;
  private final List<String> arguments;
  private final BooleanSupplier cancellation;
  private final Optional<ModulePublisher> modulePublisher;
  private final Optional<JavaApplicationEntrypoint> javaApplicationEntrypoint;
  private final String applicationPackage;
  private final JarBindingRuntime jarBindingRuntime;
  private final SystemPlatform platform;

  private ExecutionContext(Builder builder) {
    input = Objects.requireNonNull(builder.input, "input");
    output = Objects.requireNonNull(builder.output, "output");
    expectedOutput = Objects.requireNonNull(builder.expectedOutput, "expectedOutput");
    arguments = List.copyOf(builder.arguments);
    cancellation = Objects.requireNonNull(builder.cancellation, "cancellation");
    modulePublisher = Optional.ofNullable(builder.modulePublisher);
    javaApplicationEntrypoint = Optional.ofNullable(builder.javaApplicationEntrypoint);
    applicationPackage = Objects.requireNonNull(builder.applicationPackage, "applicationPackage");
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

  public Reader input() {
    return input;
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
    private Reader input = Reader.nullReader();
    private PrintWriter output = new PrintWriter(Writer.nullWriter());
    private PrintWriter expectedOutput = new PrintWriter(Writer.nullWriter());
    private List<String> arguments = List.of();
    private BooleanSupplier cancellation = () -> false;
    private ModulePublisher modulePublisher;
    private JavaApplicationEntrypoint javaApplicationEntrypoint;
    private String applicationPackage = "";
    private JarBindingRuntime jarBindingRuntime = JarBindingRuntime.unavailable();
    private SystemPlatform platform = SystemPlatform.unavailable();

    private Builder() {}

    private Builder(ExecutionContext context) {
      input = context.input;
      output = context.output;
      expectedOutput = context.expectedOutput;
      arguments = context.arguments;
      cancellation = context.cancellation;
      modulePublisher = context.modulePublisher.orElse(null);
      javaApplicationEntrypoint = context.javaApplicationEntrypoint.orElse(null);
      applicationPackage = context.applicationPackage;
      jarBindingRuntime = context.jarBindingRuntime;
      platform = context.platform;
    }

    public Builder input(Reader value) {
      input = Objects.requireNonNull(value, "value");
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
