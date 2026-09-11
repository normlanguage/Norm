package dev.w0fv1.norm.codegen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuiltinAbiGeneratorTest {
  @TempDir Path directory;

  @Test
  void preservesGeneratedBytesAndFingerprint() throws Exception {
    Path schema = Path.of(System.getProperty("norm.test.abi"));
    var golden =
        JsonParser.parseString(
                new String(
                    getClass().getResourceAsStream("/codegen/abi-golden.json").readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8))
            .getAsJsonObject();
    assertEquals(golden.get("schema").getAsString(), digest(Files.readAllBytes(schema)));
    BuiltinAbiGenerator.generate(schema, directory);
    var outputs = golden.getAsJsonObject("outputs");
    try (var files = Files.walk(directory)) {
      assertEquals(outputs.size(), files.filter(Files::isRegularFile).count());
    }
    for (var entry : outputs.entrySet()) {
      assertEquals(
          entry.getValue().getAsString(),
          digest(Files.readAllBytes(directory.resolve(entry.getKey()))),
          entry.getKey());
    }
    BuiltinAbiGenerator.generate(schema, directory);
    for (var entry : outputs.entrySet()) {
      assertEquals(
          entry.getValue().getAsString(),
          digest(Files.readAllBytes(directory.resolve(entry.getKey()))),
          entry.getKey());
    }
  }

  @Test
  void rejectsUnknownTypeReferencesBeforePublishing() throws Exception {
    var schema =
        JsonParser.parseString(Files.readString(Path.of(System.getProperty("norm.test.abi"))))
            .getAsJsonObject();
    schema
        .getAsJsonArray("builtinGlobals")
        .get(0)
        .getAsJsonObject()
        .getAsJsonObject("symbol")
        .addProperty("type", "missing");
    Path input = Files.writeString(directory.resolve("abi.json"), schema.toString());
    Path output = directory.resolve("generated");
    var failure =
        assertThrows(
            IllegalArgumentException.class, () -> BuiltinAbiGenerator.generate(input, output));
    assertTrue(failure.getMessage().contains("Unknown ABI type pattern"));
    assertFalse(Files.exists(output));
  }

  @Test
  void rejectsCyclicTypePatterns() throws Exception {
    var schema =
        JsonParser.parseString(Files.readString(Path.of(System.getProperty("norm.test.abi"))))
            .getAsJsonObject();
    var patterns = schema.getAsJsonObject("typePatterns");
    String key = patterns.keySet().iterator().next();
    var arguments = new com.google.gson.JsonArray();
    arguments.add(key);
    patterns.getAsJsonObject(key).add("arguments", arguments);
    Path input = Files.writeString(directory.resolve("abi.json"), schema.toString());
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> BuiltinAbiGenerator.generate(input, directory.resolve("output")));
    assertTrue(failure.getMessage().contains("Cyclic ABI type pattern"));
  }

  @Test
  void generatorDoesNotLoadProductClasses() {
    var location = BuiltinAbiGenerator.class.getProtectionDomain().getCodeSource().getLocation();
    assertNotEquals(
        location,
        dev.w0fv1.norm.abi.BuiltinAbi.class.getProtectionDomain().getCodeSource().getLocation());
  }

  private static String digest(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
