package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.bound.BoundExpression;
import dev.w0fv1.norm.bound.BoundIteration;
import dev.w0fv1.norm.bound.BoundPattern;
import dev.w0fv1.norm.bound.BoundStatement;
import dev.w0fv1.norm.bound.BoundWitness;
import dev.w0fv1.norm.ir.IrSchema;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class IrSchemaContractTest {
  @Test
  void schemaDefinesEveryBoundAndCoreVariantPair() throws Exception {
    assertCategory(IrSchema.EXPRESSIONS, BoundExpression.class, CoreExpression.class);
    assertCategory(IrSchema.STATEMENTS, BoundStatement.class, CoreStatement.class);
    assertCategory(IrSchema.PATTERNS, BoundPattern.class, CorePattern.class);
    assertCategory(IrSchema.ITERATIONS, BoundIteration.class, CoreIteration.class);
    assertCategory(IrSchema.WITNESS_TARGETS, BoundWitness.Target.class, CoreWitnessTarget.class);
  }

  private static void assertCategory(
      List<IrSchema.Variant> variants, Class<?> boundBase, Class<?> coreBase)
      throws ClassNotFoundException {
    Set<Class<?>> boundTypes = load(variants, true);
    Set<Class<?>> coreTypes = load(variants, false);

    assertEquals(Set.of(boundBase.getPermittedSubclasses()), boundTypes);
    assertEquals(Set.of(coreBase.getPermittedSubclasses()), coreTypes);
    assertEquals(variants.size(), variants.stream().map(IrSchema.Variant::kind).distinct().count());
  }

  private static Set<Class<?>> load(List<IrSchema.Variant> variants, boolean bound)
      throws ClassNotFoundException {
    return variants.stream()
        .map(variant -> bound ? variant.boundType() : variant.coreType())
        .map(IrSchemaContractTest::load)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Class<?> load(String name) {
    try {
      return Class.forName(name);
    } catch (ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
