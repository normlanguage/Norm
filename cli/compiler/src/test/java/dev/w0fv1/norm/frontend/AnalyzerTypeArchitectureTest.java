package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.SourceFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class AnalyzerTypeArchitectureTest {
  @Test
  void constructsDeclarationLookupBeforeAnalysisAndExposesNoMutationEntrypoints() {
    SourceFile source =
        SourceFile.of(
            Path.of("declarations.norm"),
            "Integer work(Integer value) { return value } "
                + "String work(String value) { return value }");
    ParsedDocument parsed = SourceParser.parse(source);

    DeclarationCatalog declarations =
        new DeclarationCatalog(
            List.of(parsed.syntax()),
            Set.of(source.id()),
            CompilationScope.anonymous(List.of(source)));

    assertEquals(2, declarations.functions("work").size());
    assertFalse(
        java.util.Arrays.stream(DeclarationCatalog.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().startsWith("add")));
  }

  @Test
  void delegatesConstructorFlowToAComposedPass() {
    assertFalse(
        java.util.Arrays.stream(Analyzer.class.getDeclaredMethods())
            .map(Method::getName)
            .anyMatch(name -> name.startsWith("constructor") || name.equals("binding")));
  }

  @Test
  void delegatesFileScopeConstructionToAComposedPass() {
    assertFalse(
        java.util.Arrays.stream(Analyzer.class.getDeclaredMethods())
            .map(Method::getName)
            .anyMatch(Set.of("createFileScopes", "importableSymbols")::contains));
  }

  @Test
  void delegatesImportResolutionToAComposedPass() {
    assertFalse(
        java.util.Arrays.stream(Analyzer.class.getDeclaredMethods())
            .map(Method::getName)
            .anyMatch("validateImports"::equals));
  }

  @Test
  void usesCompositionForAnnotationChecking() {
    assertFalse(AnnotationChecker.class.isAssignableFrom(Analyzer.class));
  }

  @Test
  void usesCompositionForSemanticAnalysisState() throws Exception {
    assertEquals(Object.class, Analyzer.class.getSuperclass());
    assertEquals(
        SemanticAnalysisContext.class, Analyzer.class.getDeclaredField("context").getType());
  }

  @Test
  void keepsSemanticTypesStrongThroughoutExpressionAnalysis() throws Exception {
    Set<String> typedMethods =
        Set.of(
            "typeOf",
            "analyzeArray",
            "analyzeUnary",
            "analyzeBinary",
            "analyzeCall",
            "analyzeNamedCall",
            "analyzeMethodCall",
            "memberType",
            "analyzeIndex",
            "assignmentTargetType");
    List<Class<?>> layers = new ArrayList<>();
    for (Class<?> layer = Analyzer.class; layer != Object.class; layer = layer.getSuperclass()) {
      layers.add(layer);
    }
    layers.add(ExpressionChecker.class);

    for (Class<?> layer : layers) {
      for (Method method : layer.getDeclaredMethods()) {
        if (typedMethods.contains(method.getName())) {
          assertEquals(SemanticType.class, method.getReturnType(), method::toString);
        }
        assertFalse(method.getName().equals("semanticType"), method::toString);
      }
    }

    Field expectedReturnType = SemanticAnalysisContext.class.getDeclaredField("expectedReturnType");
    assertEquals(SemanticType.class, expectedReturnType.getType());
    for (Class<?> layer : layers) {
      assertFalse(
          java.util.Arrays.stream(layer.getDeclaredClasses())
              .anyMatch(type -> type.getSimpleName().equals("DisplayTypeParser")));
    }
  }
}
