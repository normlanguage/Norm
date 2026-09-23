package dev.w0fv1.norm.codegen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BuildMetadataGenerator {
  private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

  private BuildMetadataGenerator() {}

  public static void main(String[] arguments) throws IOException {
    if (arguments.length != 3)
      throw new IllegalArgumentException(
          "Expected Norm version, GraalVM version and output directory");
    generate(arguments[0], arguments[1], Path.of(arguments[2]));
  }

  public static void generate(String normVersion, String graalVmVersion, Path output)
      throws IOException {
    Path source = output.resolve("dev/w0fv1/norm/value/BuildMetadata.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        "package dev.w0fv1.norm.value;\n\n"
            + "public final class BuildMetadata {\n"
            + "  public static final String VERSION = "
            + JSON.toJson(normVersion)
            + ";\n"
            + "  public static final String GRAALVM_VERSION = "
            + JSON.toJson(graalVmVersion)
            + ";\n\n"
            + "  private BuildMetadata() {}\n"
            + "}\n",
        StandardCharsets.UTF_8);
  }
}
