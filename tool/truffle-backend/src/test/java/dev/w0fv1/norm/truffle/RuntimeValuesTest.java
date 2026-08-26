package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionHasher;
import dev.w0fv1.norm.core.DefinitionId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class RuntimeValuesTest {
  @Test
  void comparesEnumValuesByDefinitionTypeVariantAndPayload() {
    DefinitionId first = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {1}), 0);
    DefinitionId second = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {2}), 0);
    CoreType integerResult = resultType(CoreType.INTEGER);
    CoreType stringResult = resultType(CoreType.STRING);

    assertTrue(
        RuntimeValues.equal(
            new RuntimeValues.EnumValue(first, integerResult, "Result", "Ok", List.of(1)),
            new RuntimeValues.EnumValue(first, integerResult, "Renamed", "Ok", List.of(1))));
    assertFalse(
        RuntimeValues.equal(
            new RuntimeValues.EnumValue(first, integerResult, "Result", "Ok", List.of(1)),
            new RuntimeValues.EnumValue(second, integerResult, "Result", "Ok", List.of(1))));
    assertFalse(
        RuntimeValues.equal(
            new RuntimeValues.EnumValue(first, integerResult, "Result", "Ok", List.of(1)),
            new RuntimeValues.EnumValue(first, stringResult, "Result", "Ok", List.of(1))));
    assertFalse(
        RuntimeValues.equal(
            new RuntimeValues.EnumValue(first, integerResult, "Result", "Ok", List.of(1)),
            new RuntimeValues.EnumValue(first, integerResult, "Result", "Error", List.of(1))));
    assertFalse(
        RuntimeValues.equal(
            new RuntimeValues.EnumValue(first, integerResult, "Result", "Ok", List.of(1)),
            new RuntimeValues.EnumValue(first, integerResult, "Result", "Ok", List.of(2))));
  }

  @Test
  void copiesEnumPayloadAndFormatsTheConstructedValue() {
    DefinitionId definition = new DefinitionId(DefinitionHasher.hashGroup(new byte[] {1}), 0);
    RuntimeValues.ListValue payload = new RuntimeValues.ListValue(CoreType.INTEGER);
    payload.values.add(1);
    RuntimeValues.EnumValue value =
        new RuntimeValues.EnumValue(
            definition, resultType(CoreType.INTEGER), "Result", "Ok", List.of(payload));

    RuntimeValues.EnumValue copied = (RuntimeValues.EnumValue) RuntimeValues.copy(value);
    payload.values.add(2);

    assertNotSame(value, copied);
    assertEquals(1, ((RuntimeValues.ListValue) value.field(0)).values.size());
    assertEquals(1, ((RuntimeValues.ListValue) copied.field(0)).values.size());
    assertEquals(
        "Result.Ok(1)",
        new RuntimeValues.EnumValue(
                definition, resultType(CoreType.INTEGER), "Result", "Ok", List.of(1))
            .toString());
  }

  @Test
  void givesEqualCompositeValuesTheSameLanguageHash() {
    RuntimeValues.ListValue first = new RuntimeValues.ListValue(listType(CoreType.INTEGER));
    first.values.addAll(List.of(1, 2));
    RuntimeValues.ListValue second = new RuntimeValues.ListValue(listType(CoreType.INTEGER));
    second.values.addAll(List.of(1, 2));

    assertTrue(RuntimeValues.equal(first, second));
    assertEquals(RuntimeValues.hash(first), RuntimeValues.hash(second));
  }

  @Test
  void storesResolvedRuntimeTypesForRangeAndStringBuilderValues() {
    CoreType rangeType = declaredType("Range", CoreValueCategory.VALUE);
    CoreType builderType = declaredType("StringBuilder", CoreValueCategory.VALUE);

    assertEquals(
        rangeType, RuntimeValues.runtimeType(new RuntimeValues.RangeValue(rangeType, 0, 2, 1)));
    assertEquals(
        builderType,
        RuntimeValues.runtimeType(new RuntimeValues.BuilderValue(builderType, "Norm")));
  }

  private static CoreType resultType(CoreType argument) {
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.List")),
        List.of(argument),
        CoreValueCategory.VALUE,
        CoreNullability.NON_NULL);
  }

  private static CoreType listType(CoreType argument) {
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core.List")),
        List.of(argument),
        CoreValueCategory.VALUE,
        CoreNullability.NON_NULL);
  }

  private static CoreType declaredType(String name, CoreValueCategory category) {
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId("std.core." + name)),
        List.of(),
        category,
        CoreNullability.NON_NULL);
  }
}
