package dev.w0fv1.norm.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class NativeRuntimeMetadataTest {
  @Test
  void registersTheBundledLoggingConfigurationWithAnExistingConsumer() throws Exception {
    try (var source =
        NativeRuntimeMetadataTest.class.getResourceAsStream(
            "/META-INF/native-image/dev.w0fv1/norm/reachability-metadata.json")) {
      assertNotNull(source);
      var metadata =
          JsonParser.parseReader(new InputStreamReader(source, StandardCharsets.UTF_8))
              .getAsJsonObject();
      var resource =
          metadata.getAsJsonArray("resources").asList().stream()
              .map(com.google.gson.JsonElement::getAsJsonObject)
              .filter(value -> value.get("glob").getAsString().equals("simplelogger.properties"))
              .findFirst()
              .orElseThrow();
      assertNotNull(
          Class.forName(
              resource.getAsJsonObject("condition").get("typeReached").getAsString(),
              false,
              NativeRuntimeMetadataTest.class.getClassLoader()));
      try (var properties =
          NativeRuntimeMetadataTest.class.getResourceAsStream("/simplelogger.properties")) {
        assertNotNull(properties);
        var configuration = new java.util.Properties();
        configuration.load(properties);
        assertEquals("warn", configuration.getProperty("org.slf4j.simpleLogger.defaultLogLevel"));
      }
    }
  }
}
