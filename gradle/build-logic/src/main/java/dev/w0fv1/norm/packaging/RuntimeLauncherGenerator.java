package dev.w0fv1.norm.packaging;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class RuntimeLauncherGenerator {
  public enum JavaRuntime {
    BUNDLED,
    SYSTEM
  }

  private RuntimeLauncherGenerator() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length < 4)
      throw new IllegalArgumentException(
          "Expected runtime source, output directory, module, main class and JVM arguments");
    generate(
        Path.of(arguments[1]),
        JavaRuntime.valueOf(arguments[0].toUpperCase(Locale.ROOT)),
        arguments[2],
        arguments[3],
        Arrays.asList(arguments).subList(4, arguments.length));
  }

  public static void generate(
      Path output, JavaRuntime runtime, String module, String mainClass, List<String> jvmArguments)
      throws IOException {
    if (module.isBlank() || mainClass.isBlank())
      throw new IllegalArgumentException("Module and main class must be nonempty");
    if (jvmArguments.stream().anyMatch(value -> value.isBlank() || value.matches(".*\\s.*")))
      throw new IllegalArgumentException("JVM arguments must be individual shell-safe tokens");
    Files.createDirectories(output);
    String moduleEntry = module + "/" + mainClass;
    String flags = String.join(" ", jvmArguments);
    String prefix = flags.isEmpty() ? "" : flags + " ";
    String shellJava =
        runtime == JavaRuntime.BUNDLED
            ? "exec \"$APP_HOME/runtime/bin/java\" "
            : "JAVA=java\nif [ -n \"${JAVA_HOME:-}\" ]; then JAVA=\"$JAVA_HOME/bin/java\"; fi\nexec \"$JAVA\" ";
    String shell =
        "#!/bin/sh\n"
            + "APP_HOME=$(CDPATH= cd -- \"$(dirname -- \"$0\")/..\" && pwd)\n"
            + shellJava
            + prefix
            + "--module-path \"$APP_HOME/lib\" --module "
            + moduleEntry
            + " \"$@\"\n";
    Path unix = output.resolve("norm");
    Files.writeString(unix, shell, StandardCharsets.UTF_8);
    if (!unix.toFile().setExecutable(true, false))
      throw new IOException("Cannot make Norm launcher executable: " + unix);

    String windowsJava =
        runtime == JavaRuntime.BUNDLED
            ? "\"%APP_HOME%\\runtime\\bin\\java.exe\" "
            : "if defined JAVA_HOME (set \"NORM_JAVA=%JAVA_HOME%\\bin\\java.exe\") else (set \"NORM_JAVA=java.exe\")\n\"%NORM_JAVA%\" ";
    String batch =
        "@echo off\n"
            + "setlocal\n"
            + "set \"APP_HOME=%~dp0..\"\n"
            + windowsJava
            + prefix
            + "--module-path \"%APP_HOME%\\lib\" --module "
            + moduleEntry
            + " %*\n";
    Files.writeString(
        output.resolve("norm.bat"), batch.replace("\n", "\r\n"), StandardCharsets.UTF_8);

    JsonObject descriptor = new JsonObject();
    descriptor.addProperty("module", moduleEntry);
    JsonArray flagsJson = new JsonArray();
    jvmArguments.forEach(flagsJson::add);
    descriptor.add("jvmArguments", flagsJson);
    Files.writeString(
        output.resolve("launcher.json"),
        new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(descriptor)
            + "\n",
        StandardCharsets.UTF_8);
  }
}
