package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.core.CoreBindingShape;
import dev.w0fv1.norm.core.CoreCompilationInput;
import dev.w0fv1.norm.core.CoreDefaultArgument;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.PendingDefinitionReference;
import dev.w0fv1.norm.value.ParameterPolicy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreBuildHistoryTest {
  @Test
  void storesLocalBindingsForCodeAndAuthoringContracts() {
    var owner = new CoreBuildHistory.Key.Named("Owner");
    var result = new CoreBuildHistory.Key.Named("Result");
    var defaultValue = new CoreBuildHistory.Key.Named("Default");
    var receiver =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new PendingDefinitionReference(17)),
            List.of(),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var returnType =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new PendingDefinitionReference(92)),
            List.of(),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var source =
        new CoreCompilationInput.Source(
            new CoreDefinition.MethodSignature(
                "method", receiver, List.of(), List.of(), CoreType.VOID));
    var shape =
        new CoreBindingShape.MethodSignature(
            List.of(),
            List.of(
                new CoreBindingShape.Parameter(
                    "result",
                    returnType,
                    new ParameterPolicy(true, List.of(), ParameterPolicy.LabelPolicy.NAMED),
                    Optional.of(new CoreDefaultArgument.Pending(104)))),
            CoreType.VOID);
    var unit =
        CoreBuildHistory.Unit.capture(
            source, Optional.of(shape), Map.of(17, owner, 92, result, 104, defaultValue));

    assertEquals(java.util.Set.of(0), unit.source().references());
    assertEquals(List.of(owner, result, defaultValue), unit.declarations());
    var current = Map.<CoreBuildHistory.Key, Integer>of(owner, 3, result, 4, defaultValue, 5);
    var linked = unit.source().relocate(index -> unit.declarationIndex(index, current));
    assertEquals(java.util.Set.of(3), linked.references());
    var rebound =
        unit.binding()
            .orElseThrow()
            .mapLinks(
                link ->
                    new PendingDefinitionReference(
                        unit.declarationIndex(
                            ((PendingDefinitionReference) link).declarationIndex(), current)),
                value ->
                    new CoreDefaultArgument.Pending(
                        unit.declarationIndex(
                            ((CoreDefaultArgument.Pending) value).declarationIndex(), current)));
    var parameter = rebound.parameters().getFirst();
    assertEquals(new CoreDefaultArgument.Pending(5), parameter.defaultValue().orElseThrow());
    assertEquals(
        new CoreTypeConstructor.User(new PendingDefinitionReference(4)),
        ((CoreType.Declared) parameter.type()).constructor());
  }

  @Test
  void rejectsMissingDeclarationBeforePersistingUnit() {
    var type =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new PendingDefinitionReference(9)),
            List.of(),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var source =
        new CoreCompilationInput.Source(
            new CoreDefinition.MethodSignature(
                "method", type, List.of(), List.of(), CoreType.VOID));
    assertThrows(
        NullPointerException.class,
        () -> CoreBuildHistory.Unit.capture(source, Optional.empty(), Map.of()));
  }
}
