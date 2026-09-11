package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class FrontendBoundaryTest {
  @Test
  void typeResolutionDoesNotRegisterDeclarationsOrInterpretPolicies() {
    Set<String> declarationMethods =
        Set.of(
            "register",
            "registerDeclaration",
            "registerTypeParameters",
            "parameters",
            "parametersOf",
            "fieldParameters",
            "symbolTypeParameters",
            "managedField",
            "managedImplementation",
            "resultBuilder",
            "annotationPolicy");
    assertTrue(
        Arrays.stream(TypeResolver.class.getDeclaredMethods())
            .noneMatch(method -> declarationMethods.contains(method.getName())));
    assertThrows(
        ClassNotFoundException.class, () -> Class.forName("dev.w0fv1.norm.frontend.TypeSystem"));
  }

  @Test
  void analysisStateHasNoUnrestrictedFields() {
    for (Class<?> owner : List.of(BodyAnalysisState.class, TypeResolutionState.class)) {
      for (var field : owner.getDeclaredFields()) {
        if (!field.isSynthetic())
          assertTrue(Modifier.isPrivate(field.getModifiers()), field.toString());
      }
    }
  }

  @Test
  void passesReceiveTheirActualDependenciesInsteadOfTheEntireAnalysis() {
    for (Class<?> pass :
        List.of(
            TypeResolver.class,
            DeclarationPolicyResolver.class,
            DeclarationAnalyzer.class,
            BodyAnalyzer.class,
            ExpressionChecker.class,
            AnnotationChecker.class,
            CollectionAnalyzer.class,
            ClosureAnalyzer.class,
            PatternAnalyzer.class)) {
      assertTrue(
          Arrays.stream(pass.getDeclaredFields())
              .noneMatch(field -> field.getType() == SemanticAnalysisContext.class),
          pass.getName());
      for (var constructor : pass.getDeclaredConstructors()) {
        assertFalse(
            List.of(constructor.getParameterTypes()).contains(SemanticAnalysisContext.class),
            constructor.toString());
      }
    }
  }
}
