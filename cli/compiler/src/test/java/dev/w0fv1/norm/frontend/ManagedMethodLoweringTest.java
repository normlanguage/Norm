package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.CoreCanonicalizer;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionRole;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ManagedMethodLoweringTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "class Child<T> extends Repository<T> { T? find(Long id) }",
        "class Child<T> extends Repository<T> { T? find(Long id) { null } }"
      })
  void preservesGenericDeclarationsWithoutInventingExecutableBodies(String child) {
    var source =
        SourceFile.of(
            Path.of("repository.norm"),
            """
            class Repository<T> {
              T? find(Long id)
              R convert<R>(T item, R fallback)
              Void clear()
              Void close() {}
            }
            T? load<T>(Repository<T> repository, Long id) { repository.find(id) }
            Function<T?(Long)> reader<T>(Repository<T> repository) { repository.find }
            T? loadNullable<T>(Repository<T>? repository, Long id) { repository?.find(id) }
            R convert<T, R>(Repository<T> repository, T item, R fallback) {
              repository.convert<R>(item: item, fallback: fallback)
            }
            Void main() {}
            """
                + child);
    try (var compiler = new CompilerSession()) {
      var snapshot = compiler.snapshot(source);
      var programs =
          snapshot.documentIds().stream()
              .map(id -> snapshot.document(id).orElseThrow().syntax())
              .toList();
      var bound =
          new Binder(programs, snapshot.semanticModel())
              .bind(snapshot.analysis().entryPoint().orElseThrow());
      var find =
          bound.callables().stream()
              .filter(value -> value.name().equals("find"))
              .findFirst()
              .orElseThrow();
      assertTrue(find.implementation().isEmpty());
      assertThrows(IllegalStateException.class, find::body);
      var close =
          bound.callables().stream()
              .filter(value -> value.name().equals("close"))
              .findFirst()
              .orElseThrow();
      assertTrue(close.implementation().isPresent());
      assertTrue(close.body().statements().isEmpty());
      var coordinates =
          snapshot.documentIds().stream()
              .collect(
                  Collectors.toMap(
                      id -> id,
                      id ->
                          new ModuleSourceCoordinate(
                              ModuleCoordinate.localApplication(),
                              "source"
                                  + snapshot.documentIds().stream().sorted().toList().indexOf(id)
                                  + ".norm")));
      var core = new BoundCoreConverter(bound, coordinates).convert();
      assertDoesNotThrow(
          () ->
              new CoreProgram(
                  new CoreCanonicalizer()
                      .canonicalize(
                          core.declarations().stream()
                              .map(BoundCoreConverter.Declaration::definition)
                              .toList())
                      .groups()));
      var signatures =
          core.declarations().stream()
              .filter(value -> value.role() == CoreDefinitionRole.METHOD_SIGNATURE)
              .map(
                  value ->
                      assertInstanceOf(CoreDefinition.MethodSignature.class, value.definition()))
              .filter(
                  value ->
                      value.name().equals("find")
                          || value.name().equals("convert")
                          || value.name().equals("clear"))
              .collect(
                  Collectors.toMap(
                      CoreDefinition.MethodSignature::name,
                      value -> value,
                      (first, second) -> first));
      assertEquals(3, signatures.size());
      assertEquals(1, signatures.get("find").parameterTypes().size());
      assertEquals(CoreType.LONG, signatures.get("find").parameterTypes().getFirst());
      assertEquals(
          new CoreType.Parameter(0, CoreNullability.NULLABLE), signatures.get("find").returnType());
      assertEquals(1, signatures.get("convert").typeParameters().size());
      assertEquals(2, signatures.get("convert").parameterTypes().size());
      assertEquals(
          new CoreType.Parameter(0, CoreNullability.NON_NULL),
          signatures.get("convert").parameterTypes().getFirst());
      assertEquals(
          new CoreType.Parameter(1, CoreNullability.NON_NULL),
          signatures.get("convert").returnType());
      assertEquals(
          signatures.get("convert").returnType(),
          signatures.get("convert").parameterTypes().getLast());
      assertEquals(CoreType.VOID, signatures.get("clear").returnType());
    }
  }
}
