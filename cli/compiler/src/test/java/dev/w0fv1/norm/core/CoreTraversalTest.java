package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class CoreTraversalTest {
  @Test
  void visitsCallableCaptureAbiDirectly() {
    PendingDefinitionReference captureLink = new PendingDefinitionReference(0);
    PendingDefinitionReference localLink = new PendingDefinitionReference(0);
    CoreType captureType = userType(captureLink);
    CoreDefinition.Callable callable =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(),
            List.of(captureType),
            List.of(0),
            List.of(),
            List.of(),
            List.of(),
            CoreType.VOID,
            List.of(new CoreLocal(0, userType(localLink), CoreLocal.Kind.CAPTURE)),
            new CoreBlock(0, List.of()));

    List<CoreDefinitionLink> links = CoreTree.links(callable);

    assertEquals(1, links.size());
    assertSame(captureLink, links.getFirst());
  }

  @Test
  void rewritesEveryCallableCaptureAbiLink() {
    CoreType captureType = userType(new PendingDefinitionReference(0));
    CoreDefinition.Callable callable = callable(captureType, new CoreBlock(0, List.of()));
    DefinitionReference replacement = new DefinitionReference.RecursiveMember(0);

    CoreDefinition.Callable rewritten =
        (CoreDefinition.Callable) CoreTree.resolve(callable, ignored -> replacement);

    assertEquals(replacement, userLink(rewritten.captureTypes().getFirst()));
    assertEquals(replacement, userLink(rewritten.locals().getFirst().type()));
  }

  @Test
  void keepsAuthoringReferencesSeparateFromTypeDependencies() {
    DefinitionReference capture = external(1);
    DefinitionReference target = external(2);
    CoreExpression.Call call =
        new CoreExpression.Call(
            2, target, Optional.empty(), List.of(), List.of(), false, CoreType.VOID);
    CoreDefinition.Callable callable =
        callable(
            userType(capture),
            new CoreBlock(0, List.of(new CoreStatement.ExpressionStatement(1, call))));

    assertEquals(java.util.Map.of(2, target), CoreTree.referenceSites(callable));
  }

  private static CoreDefinition.Callable callable(CoreType captureType, CoreBlock body) {
    return new CoreDefinition.Callable(
        Optional.empty(),
        List.of(),
        List.of(captureType),
        List.of(0),
        List.of(),
        List.of(),
        List.of(),
        CoreType.VOID,
        List.of(new CoreLocal(0, captureType, CoreLocal.Kind.CAPTURE)),
        body);
  }

  private static CoreType userType(CoreDefinitionLink link) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(link),
        List.of(),
        CoreValueCategory.IDENTITY,
        CoreNullability.NON_NULL);
  }

  private static CoreDefinitionLink userLink(CoreType type) {
    CoreType.Declared declared = assertInstanceOf(CoreType.Declared.class, type);
    return assertInstanceOf(CoreTypeConstructor.User.class, declared.constructor()).definition();
  }

  private static DefinitionReference external(int discriminator) {
    return new DefinitionReference.External(
        new DefinitionId(DefinitionHasher.hashGroup(new byte[] {(byte) discriminator}), 0));
  }
}
