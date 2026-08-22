package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.w0fv1.norm.semantic.SemanticType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class AnalyzerTypeArchitectureTest {
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

    for (Method method : Analyzer.class.getDeclaredMethods()) {
      if (typedMethods.contains(method.getName())) {
        assertEquals(SemanticType.class, method.getReturnType(), method::toString);
      }
      assertFalse(method.getName().equals("semanticType"), method::toString);
    }

    Field expectedReturnType = Analyzer.class.getDeclaredField("expectedReturnType");
    assertEquals(SemanticType.class, expectedReturnType.getType());
    assertFalse(
        java.util.Arrays.stream(Analyzer.class.getDeclaredClasses())
            .anyMatch(type -> type.getSimpleName().equals("DisplayTypeParser")));
  }
}
