package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class CoreRuntimeTypeTest {
  @Test
  void canonicalizesCaptureOrderBeforeHashing() {
    CoreType pair =
        new CoreType.Declared(
            new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Pair")),
            List.of(
                new CoreType.Parameter(0, CoreNullability.NON_NULL),
                new CoreType.Parameter(1, CoreNullability.NON_NULL)),
            CoreValueCategory.VALUE,
            CoreNullability.NON_NULL);
    CoreType template =
        new CoreType.Declared(
            new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.Array")),
            List.of(pair),
            CoreValueCategory.VALUE,
            CoreNullability.NON_NULL);
    CoreRuntimeType forward =
        new CoreRuntimeType(
            template, List.of(new CoreTypeCapture(0, 0), new CoreTypeCapture(1, 1)));
    CoreRuntimeType reverse =
        new CoreRuntimeType(
            template, List.of(new CoreTypeCapture(1, 1), new CoreTypeCapture(0, 0)));

    assertEquals(forward, reverse);
    assertEquals(group(forward).id(), group(reverse).id());
  }

  @Test
  void rejectsDuplicateAndNegativeCaptureIndices() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new CoreRuntimeType(
                new CoreType.Parameter(0, CoreNullability.NON_NULL),
                List.of(new CoreTypeCapture(0, 0), new CoreTypeCapture(0, 1))));
    assertThrows(IllegalArgumentException.class, () -> new CoreTypeCapture(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> new CoreTypeCapture(0, -1));
  }

  private static CoreDefinitionGroup group(CoreRuntimeType runtimeType) {
    CoreExpression.ArrayLiteral literal =
        new CoreExpression.ArrayLiteral(2, List.of(), runtimeType, runtimeType.template());
    CoreDefinition.Callable callable =
        new CoreDefinition.Callable(
            java.util.Optional.empty(),
            List.of(),
            List.of(),
            List.of(0, 1),
            CoreType.VOID,
            List.of(
                new CoreLocal(0, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE),
                new CoreLocal(1, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE)),
            new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, literal))));
    return CoreDefinitionGroup.create(List.of(callable));
  }
}
