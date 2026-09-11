package dev.w0fv1.norm.testing;

import com.google.gson.Gson;
import com.sun.management.ThreadMXBean;
import dev.w0fv1.norm.core.CompilationResult;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompilerBenchmark {
  private static final int WARMUP = 5;
  private static final int SAMPLES = 10;
  private static final ThreadMXBean ALLOCATION = (ThreadMXBean) ManagementFactory.getThreadMXBean();

  private CompilerBenchmark() {}

  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("Expected workload path");
    if (!ALLOCATION.isThreadAllocatedMemorySupported())
      throw new IllegalStateException("Thread allocation counters are unavailable");
    ALLOCATION.setThreadAllocatedMemoryEnabled(true);
    Path input = Path.of(args[0]).toAbsolutePath().normalize();
    String text = Files.readString(input);
    SourceFile source = SourceFile.of(input, text);
    if (!text.contains("Integer bias() { return 1 }"))
      throw new IllegalArgumentException("Workload is missing the incremental edit anchor");
    NormRuntime runtime = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(runtime);
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("javaVersion", System.getProperty("java.runtime.version"));
    report.put("javaHome", System.getProperty("java.home"));
    report.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
    report.put("workloadSha256", digest(input));
    Path compiler =
        Path.of(CompilerSession.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    report.put("compiler", compiler.toString());
    report.put("compilerSha256", digest(compiler));
    report.put("warmup", WARMUP);
    report.put("samples", SAMPLES);
    report.put(
        "compile",
        measure(
            () -> {
              try (var session = environment.compilerSession()) {
                requireCompilation(session.compile(source));
              }
            }));
    try (var session = environment.compilerSession()) {
      if (session.analyze(source).hasErrors())
        throw new IllegalStateException("Initial analysis failed");
      int[] revision = {0};
      report.put(
          "incrementalAnalysis",
          measure(
              () -> {
                String changed =
                    (++revision[0] % 2 == 0)
                        ? text
                        : text.replace(
                            "Integer bias() { return 1 }", "Integer bias() { return 2 }");
                if (session.analyze(SourceFile.of(input, changed)).hasErrors())
                  throw new IllegalStateException("Incremental analysis failed");
              }));
    }
    CompilationResult compiled;
    try (var session = environment.compilerSession()) {
      compiled = session.compile(source);
      requireCompilation(compiled);
    }
    var artifact = compiled.output().orElseThrow().artifact();
    report.put(
        "execution",
        measure(
            () -> {
              StringWriter output = new StringWriter();
              runtime.run(artifact, ExecutionContext.of(new PrintWriter(output)));
              if (!output.toString().equals("10000" + System.lineSeparator()))
                throw new IllegalStateException("Unexpected execution output: " + output);
            }));
    System.out.println(new Gson().toJson(report));
  }

  private static List<Map<String, Object>> measure(Runnable work) {
    List<Map<String, Object>> samples = new ArrayList<>();
    for (int iteration = -WARMUP; iteration < SAMPLES; iteration++) {
      long beforeAllocation = ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId());
      long before = System.nanoTime();
      work.run();
      long elapsed = System.nanoTime() - before;
      long allocated =
          ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId()) - beforeAllocation;
      if (iteration >= 0)
        samples.add(Map.of("nanoseconds", elapsed, "mainThreadAllocatedBytes", allocated));
    }
    return List.copyOf(samples);
  }

  private static void requireCompilation(CompilationResult compilation) {
    if (!compilation.isSuccess())
      throw new IllegalStateException(compilation.diagnostics().toString());
  }

  private static String digest(Path path) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
  }
}
