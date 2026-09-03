package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.junit.jupiter.api.Test;

final class NativeImageMetadataTest {
  @Test
  void preservesCustomLanguageServerRequestsForNativeReflection() throws Exception {
    Set<String> requests =
        Arrays.stream(LanguageServer.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(JsonRequest.class))
            .map(java.lang.reflect.Method::getName)
            .collect(Collectors.toSet());
    try (var stream =
            NativeImageMetadataTest.class.getResourceAsStream(
                "/META-INF/native-image/dev.w0fv1.norm/compiler/reachability-metadata.json");
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      var server =
          JsonParser.parseReader(reader)
              .getAsJsonObject()
              .getAsJsonArray("reflection")
              .asList()
              .stream()
              .map(element -> element.getAsJsonObject())
              .filter(type -> type.get("type").getAsString().equals(LanguageServer.class.getName()))
              .findFirst()
              .orElseThrow();
      Set<String> preserved =
          server.getAsJsonArray("methods").asList().stream()
              .map(method -> method.getAsJsonObject().get("name").getAsString())
              .collect(Collectors.toSet());
      assertEquals(requests, preserved);
    }
  }
}
