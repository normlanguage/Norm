package dev.w0fv1.norm.cli.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.w0fv1.norm.jvm.LinkedJarBinding;
import dev.w0fv1.norm.project.ApplicationCompilation;
import dev.w0fv1.norm.project.ProjectSourceSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.objectweb.asm.Type;

final class NativeImageConfigurationWriter {
  NativeImageConfiguration write(
      ApplicationCompilation compilation,
      java.util.List<LinkedJarBinding> bindings,
      Path destination)
      throws IOException {
    Path configuration =
        destination
            .resolve("META-INF")
            .resolve("native-image")
            .resolve("norm")
            .resolve("application");
    Files.createDirectories(configuration);
    JsonObject metadata = new JsonObject();
    metadata.add("reflection", reflection(compilation, bindings));
    metadata.add("resources", resources(compilation.sourceSet()));
    Files.writeString(
        configuration.resolve("reachability-metadata.json"),
        metadata.toString(),
        StandardCharsets.UTF_8);
    return new NativeImageConfiguration(destination);
  }

  private static JsonArray reflection(
      ApplicationCompilation compilation, java.util.List<LinkedJarBinding> bindings)
      throws IOException {
    Map<String, ReflectedClass> classes = new TreeMap<>();
    var materializable =
        compilation
            .result()
            .program()
            .orElseThrow()
            .compilation()
            .artifact()
            .program()
            .aggregates()
            .stream()
            .filter(
                aggregate ->
                    switch (aggregate.kind()) {
                      case CLASS, VALUE -> true;
                      case ANNOTATION -> false;
                    })
            .map(
                aggregate ->
                    dev.w0fv1.norm.jvm.JavaApplicationTypeName.binaryName(aggregate.nominalType()))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    for (LinkedJarBinding binding : bindings) {
      binding.classDescriptors().values().stream()
          .map(NativeImageConfigurationWriter::descriptorName)
          .forEach(name -> reflect(classes, name));
    }
    compilation.annotationOutput().stream()
        .flatMap(output -> output.stubs().stream())
        .forEach(
            stub -> {
              String name = stub.binaryName();
              var reflected = reflect(classes, name);
              reflected.allDeclaredMembers = true;
              reflected.unsafeAllocated = materializable.contains(name);
              int separator = name.lastIndexOf('.');
              if (separator >= 0)
                reflect(classes, name.substring(0, separator + 1) + "package-info");
            });
    JsonArray values = new JsonArray();
    classes.values().forEach(value -> values.add(value.json()));
    return values;
  }

  static String descriptorName(String descriptor) {
    return Type.getType(descriptor).getClassName();
  }

  private static ReflectedClass reflect(Map<String, ReflectedClass> classes, String name) {
    return classes.computeIfAbsent(name, ReflectedClass::new);
  }

  private static JsonArray resources(ProjectSourceSet sourceSet) {
    JsonArray resources = new JsonArray();
    sourceSet.resources().keySet().stream()
        .sorted()
        .forEach(
            path -> {
              JsonObject entry = new JsonObject();
              entry.addProperty("glob", path.replace("*", "\\*"));
              resources.add(entry);
            });
    return resources;
  }

  record NativeImageConfiguration(Path classpath) {}

  private static final class ReflectedClass {
    private final String name;
    private boolean allDeclaredMembers;
    private boolean unsafeAllocated;

    private ReflectedClass(String name) {
      this.name = name;
    }

    private JsonObject json() {
      JsonObject result = new JsonObject();
      result.addProperty("type", name);
      if (unsafeAllocated) result.addProperty("unsafeAllocated", true);
      if (allDeclaredMembers) {
        result.addProperty("allDeclaredFields", true);
        result.addProperty("allDeclaredConstructors", true);
        result.addProperty("allDeclaredMethods", true);
      }
      return result;
    }
  }
}
