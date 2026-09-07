package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.jvm.JavaApplicationTypeName;
import dev.w0fv1.norm.jvm.LinkedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.project.ApplicationCompilation;
import dev.w0fv1.norm.runtime.NativeApplicationArchive;
import dev.w0fv1.norm.runtime.NativeApplicationData;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class NativeApplicationExecutable {
  private final NativeToolchainClasspath.Plan toolchain;

  public NativeApplicationExecutable() throws IOException {
    var paths = new LinkedHashSet<Path>();
    addPathEntries(paths, System.getProperty("java.class.path", ""), false);
    addPathEntries(paths, System.getProperty("jdk.module.path", ""), true);
    try (var input =
        NativeApplicationExecutable.class.getResourceAsStream("/toolchain-artifacts.json")) {
      if (input == null) throw new IOException("Toolchain artifact catalog is unavailable");
      try (var reader = new java.io.InputStreamReader(input, StandardCharsets.UTF_8)) {
        toolchain =
            NativeToolchainClasspath.plan(
                com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(),
                List.copyOf(paths));
      }
    }
  }

  public List<dev.w0fv1.norm.jvm.ResolvedJarGraph> supportGraphs() {
    return toolchain.graphs();
  }

  public Path write(
      ApplicationCompilation compilation,
      Path destination,
      Consumer<String> buildOutput,
      boolean diagnostics)
      throws IOException {
    Path output = destination.toAbsolutePath().normalize();
    Path parent = output.getParent();
    if (parent == null) throw new IOException("application executable has no parent directory");
    Files.createDirectories(parent);
    try (var workspace = new dev.w0fv1.norm.utils.TemporaryDirectory();
        var report = NativeBuildReport.create(output, buildOutput, diagnostics)) {
      Path staging = workspace.path();
      if (diagnostics) report.accept("Native build diagnostics: " + report.directory());
      Path archive = staging.resolve("application.bin");
      var applicationIndex =
          compilation.annotationOutput().isPresent()
              ? dev.w0fv1.norm.jvm.JavaApplicationMethodIndex.analyze(
                  compilation.annotationOutput().orElseThrow().classes(),
                  compilation.annotationOutput().orElseThrow().stubs())
              : new dev.w0fv1.norm.jvm.JavaApplicationMethodIndex.Analysis(
                  java.util.Map.of(), Set.of());
      var applicationMethods = applicationIndex.instanceMethods();
      report.applicationMethods(applicationMethods);
      var original = compilation.result().program().orElseThrow().compilation().artifact();
      Set<String> hostTypes =
          compilation.annotationOutput().stream()
              .flatMap(value -> value.stubs().stream())
              .map(stub -> stub.binaryName())
              .collect(java.util.stream.Collectors.toSet());
      Set<dev.w0fv1.norm.core.DefinitionId> hostEntries =
          new LinkedHashSet<>(applicationIndex.entryPoints());
      for (var definition : original.program().definitions()) {
        var nominal =
            switch (definition.definition()) {
              case dev.w0fv1.norm.core.CoreDefinition.Aggregate type -> type.nominalType();
              case dev.w0fv1.norm.core.CoreDefinition.Interface type -> type.nominalType();
              case dev.w0fv1.norm.core.CoreDefinition.Enum type -> type.nominalType();
              default -> null;
            };
        if (nominal != null && hostTypes.contains(JavaApplicationTypeName.binaryName(nominal)))
          hostEntries.add(definition.id());
      }
      var retention = dev.w0fv1.norm.core.CoreReachability.analyze(original, hostEntries);
      report.coreRetention(retention);
      var retained = retention.artifact();
      var execution =
          dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(
              retained, applicationIndex.entryPoints());
      var calls = dev.w0fv1.norm.core.CoreReachability.jarCalls(retained, execution);
      report.accept(
          "Execution plan: " + execution.callables().size() + " callable identities selected");
      var bindings =
          compilation.sourceSet().jarBindings().stream()
              .map(LinkedJarBinding::from)
              .map(binding -> calls.map(binding::retainCalls).orElse(binding))
              .toList();
      var javaClasspath = compilation.javaClasspath();
      toolchain.validate(javaClasspath.artifacts());
      report.javaArtifacts(javaClasspath.artifacts());
      report.accept(
          "Reachability: "
              + original.program().definitions().size()
              + " -> "
              + retained.program().definitions().size()
              + " Core definitions; "
              + bindings.stream().mapToInt(binding -> binding.calls().size()).sum()
              + " Java calls retained"
              + (calls.isEmpty() ? " (dynamic binding lookup)" : ""));
      NativeApplicationArchive.write(
          new NativeApplicationData(
              retained,
              execution,
              bindings,
              JavaApplicationTypeName.packageName(
                  SourceHeader.parse(compilation.sourceSet().primarySource())
                      .packageName()
                      .orElse(""))),
          archive);
      var configuration =
          new NativeImageConfigurationWriter()
              .write(compilation, bindings, staging.resolve("native-image"));
      var javaPaths = new ArrayList<Path>(toolchain.unmanaged());
      javaPaths.addAll(javaClasspath.paths());
      compilation.annotationOutput().map(value -> value.classes()).ifPresent(javaPaths::add);
      var urls = new java.net.URL[javaPaths.size()];
      for (int index = 0; index < urls.length; index++)
        urls[index] = javaPaths.get(index).toUri().toURL();
      try (var loader = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader())) {
        new dev.w0fv1.norm.jvm.JavaDirectCallBundle()
            .write(bindings, configuration.classpath(), loader);
      }
      var reachability =
          new NativeReachabilityMetadata().prepare(javaClasspath, staging.resolve("reachability"));
      if (diagnostics)
        Files.copy(
            reachability.manifest(), report.directory().resolve("reachability-sources.json"));
      if (reachability.configurationCount() > 0) {
        report.accept(
            "Using "
                + reachability.configurationCount()
                + " GraalVM reachability metadata configuration(s)");
      }
      Path imageBase = staging.resolve(stripExecutableSuffix(output.getFileName().toString()));
      Path arguments = report.directory().resolve("native-image.args");
      var imageClasspath =
          classpath(
              compilation,
              configuration.classpath(),
              reachability.classpath(),
              javaClasspath.artifacts(),
              toolchain.unmanaged());
      var imageArguments =
          arguments(compilation, archive, imageClasspath, imageBase, report.arguments());
      Files.writeString(
          arguments,
          imageArguments.stream()
              .map(NativeApplicationExecutable::argument)
              .collect(
                  java.util.stream.Collectors.joining(
                      System.lineSeparator(), "", System.lineSeparator())),
          StandardCharsets.UTF_8);
      var imageToolchain = NativeImageToolchain.ensureAvailable(report);
      report.buildInputs(imageArguments, imageClasspath, archive, imageToolchain.executable());
      int status = imageToolchain.run(List.of("@" + arguments), report);
      if (status != 0) throw new IOException("Native Image exited with code " + status);
      Path image = nativeImageOutput(imageBase);
      if (!Files.isRegularFile(image)) {
        throw new IOException("Native Image did not create " + image);
      }
      var artifacts = NativeBuildArtifacts.read(staging, image);
      if (diagnostics)
        Files.copy(
            staging.resolve("build-artifacts.json"),
            report.directory().resolve("build-artifacts.json"));
      var delivered = NativeApplicationDelivery.publish(artifacts, output);
      report.accept("Native runtime delivery: " + delivered.size() + " file(s)");
      report.complete(output, delivered);
      return output;
    } catch (java.io.UncheckedIOException exception) {
      throw exception.getCause();
    }
  }

  private static List<String> arguments(
      ApplicationCompilation compilation,
      Path archive,
      List<Path> classpath,
      Path imageBase,
      List<String> reportArguments)
      throws IOException {
    List<String> arguments = new ArrayList<>();
    arguments.add("-Dtruffle.UseFallbackRuntime=true");
    arguments.add("-Dpolyglot.engine.WarnInterpreterOnly=false");
    arguments.add("--enable-native-access=ALL-UNNAMED,org.graalvm.truffle");
    arguments.add("--initialize-at-build-time=dev.w0fv1.norm");
    arguments.add(
        "--initialize-at-run-time=dev.w0fv1.norm.truffle.JsonRuntime,dev.w0fv1.norm.truffle.YamlRuntime,dev.w0fv1.norm.truffle.JacksonDataRuntime,io.netty");
    arguments.add("--features=dev.w0fv1.norm.runtime.NativeApplicationFeature");
    arguments.add("-H:+UnlockExperimentalVMOptions");
    arguments.addAll(NativeImageCompatibility.arguments(compilation));
    arguments.add("-H:+ReportExceptionStackTraces");
    arguments.addAll(reportArguments);
    arguments.add("-H:-UnlockExperimentalVMOptions");
    arguments.add("-J-Dnorm.native.application.archive=" + archive);
    arguments.add("-cp");
    arguments.add(
        classpath.stream()
            .map(Path::toString)
            .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator)));
    arguments.add("-o");
    arguments.add(imageBase.toString());
    arguments.add("dev.w0fv1.norm.runtime.NativeApplicationMain");
    return List.copyOf(arguments);
  }

  private static List<Path> classpath(
      ApplicationCompilation compilation,
      Path configurationClasspath,
      Path reachabilityClasspath,
      List<ResolvedJarArtifact> applicationClasspath,
      List<Path> toolchainClasses)
      throws IOException {
    Set<Path> paths = new LinkedHashSet<>(toolchainClasses);
    paths.add(configurationClasspath);
    paths.add(reachabilityClasspath);
    compilation
        .annotationOutput()
        .map(output -> output.classes())
        .filter(Files::isDirectory)
        .ifPresent(paths::add);
    applicationClasspath.stream().map(ResolvedJarArtifact::file).forEach(paths::add);
    return paths.stream().map(Path::toAbsolutePath).map(Path::normalize).toList();
  }

  private static void addPathEntries(Set<Path> paths, String value, boolean expandDirectories)
      throws IOException {
    if (value.isBlank()) return;
    for (String entry : value.split(java.io.File.pathSeparator)) {
      if (entry.isBlank()) continue;
      Path path = Path.of(entry).toAbsolutePath().normalize();
      if (expandDirectories && Files.isDirectory(path)) {
        try (var children = Files.list(path)) {
          children
              .filter(file -> file.getFileName().toString().endsWith(".jar"))
              .sorted()
              .forEach(paths::add);
        }
      } else {
        paths.add(path);
      }
    }
  }

  private static String argument(String value) {
    return '"' + value.replace("\\", "/").replace("\"", "\\\"") + '"';
  }

  private static String stripExecutableSuffix(String name) {
    return name.toLowerCase(java.util.Locale.ROOT).endsWith(".exe")
        ? name.substring(0, name.length() - 4)
        : name;
  }

  private static Path nativeImageOutput(Path base) {
    return System.getProperty("os.name", "").startsWith("Windows")
        ? base.resolveSibling(base.getFileName() + ".exe")
        : base;
  }
}
