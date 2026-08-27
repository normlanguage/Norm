package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CoreTraversalContractTest {
  private static final List<Class<?>> SEALED_NODES =
      List.of(
          CoreDefinition.class,
          CoreStatement.class,
          CoreExpression.class,
          CorePattern.class,
          CoreIteration.class,
          CoreType.class,
          CoreTypeConstructor.class,
          CoreWitnessTarget.class);

  @Test
  void walkerFindsEveryDependencyEncodedByTheCodec() {
    for (CoreDefinition definition : definitions()) {
      Set<CoreDefinitionLink> encoded = new HashSet<>();

      CoreCodec.encodeDefinition(
          definition,
          link -> {
            encoded.add(link);
            return external(link);
          });

      assertEquals(encoded, Set.copyOf(CoreTree.links(definition)), definition::toString);
    }
  }

  @Test
  void rewriterEliminatesEveryPendingReference() {
    for (CoreDefinition definition : definitions()) {
      CoreDefinition resolved = CoreTree.resolve(definition, CoreTraversalContractTest::external);

      assertTrue(
          CoreTree.links(resolved).stream().allMatch(DefinitionReference.class::isInstance),
          definition::toString);
      CoreCodec.encodeGroup(List.of(resolved));
    }
  }

  @Test
  void fixtureCoversEverySealedCoreNodeVariant() {
    Set<Class<?>> encountered = new HashSet<>();
    IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
    definitions().forEach(definition -> collectVariants(definition, encountered, visited));

    for (Class<?> node : SEALED_NODES) {
      assertEquals(
          Set.of(node.getPermittedSubclasses()), intersection(node, encountered), node::getName);
    }
  }

  private static List<CoreDefinition> definitions() {
    Links links = new Links();
    CoreType capture = links.type();
    CoreType parameter = links.type();
    CoreType result = links.type();
    List<CoreExpression> expressions = expressions(links);
    List<CoreStatement> statements = new ArrayList<>();
    statements.add(new CoreStatement.LocalDeclaration(100, 3, expressions.get(0)));
    statements.add(new CoreStatement.LocalAssignment(101, 3, expressions.get(1)));
    statements.add(
        new CoreStatement.FieldAssignment(
            102, expressions.get(2), new CoreFieldReference(links.next(), 0), expressions.get(3)));
    statements.add(
        new CoreStatement.IntrinsicAssignment(
            103,
            intrinsic(),
            expressions.get(4),
            Optional.of(expressions.get(5)),
            expressions.get(6)));
    statements.add(
        new CoreStatement.ReferenceAssignment(
            113, expressions.get(expressions.size() - 3), expressions.get(0)));
    expressions.forEach(
        expression -> statements.add(new CoreStatement.ExpressionStatement(104, expression)));
    statements.add(
        new CoreStatement.IfStatement(
            105, expressions.get(0), block(expressions.get(1)), block(expressions.get(2))));
    statements.add(
        new CoreStatement.ConditionalForStatement(
            106, expressions.get(3), block(expressions.get(4))));
    statements.add(
        new CoreStatement.ForStatement(
            107,
            3,
            3,
            OptionalInt.empty(),
            expressions.get(5),
            block(expressions.get(6)),
            new CoreIteration.Builtin(intrinsic())));
    statements.add(
        new CoreStatement.ForStatement(
            108,
            3,
            3,
            OptionalInt.of(3),
            expressions.get(7),
            block(expressions.get(8)),
            new CoreIteration.Interface(links.next(), links.next(), links.next())));
    statements.add(new CoreStatement.ReturnStatement(109, Optional.of(expressions.get(9))));
    statements.add(new CoreStatement.YieldStatement(110, expressions.get(10)));
    statements.add(new CoreStatement.BreakStatement(111));
    statements.add(new CoreStatement.ContinueStatement(112));
    statements.add(
        new CoreStatement.TryStatement(
            114,
            block(expressions.get(11)),
            List.of(new CoreCatchClause(links.type(), 3, block(expressions.get(12)))),
            Optional.of(block(expressions.get(13)))));
    statements.add(new CoreStatement.ThrowStatement(115, expressions.get(14)));
    CoreDefinition.Callable callable =
        new CoreDefinition.Callable(
            Optional.empty(),
            List.of(new CoreTypeParameter(0, Optional.of(links.type()))),
            List.of(capture),
            List.of(0),
            List.of(parameter),
            List.of(1),
            List.of(2),
            result,
            List.of(
                new CoreLocal(0, capture, CoreLocal.Kind.CAPTURE),
                new CoreLocal(1, parameter, CoreLocal.Kind.PARAMETER),
                new CoreLocal(2, CoreType.DYNAMIC, CoreLocal.Kind.REIFIED_TYPE),
                new CoreLocal(3, links.type(), CoreLocal.Kind.VARIABLE)),
            new CoreBlock(0, statements));
    CoreWitness callableWitness =
        new CoreWitness(links.next(), new CoreWitnessTarget.Callable(links.next()));
    CoreWitness intrinsicWitness =
        new CoreWitness(links.next(), new CoreWitnessTarget.Intrinsic(intrinsic()));
    List<CoreDefinition> definitions = new ArrayList<>();
    definitions.add(
        new CoreDefinition.Annotation(
            nominal("Annotation"),
            Set.of(AnnotationTarget.TYPE),
            AnnotationRetention.RUNTIME,
            List.of(new CoreField(0, links.type())),
            List.of(Optional.of(new CoreAnnotationValue(links.type(), "value")))));
    definitions.addAll(
        List.of(
            callable,
            new CoreDefinition.Aggregate(
                nominal("Class"),
                CoreValueCategory.IDENTITY,
                List.of(new CoreTypeParameter(0, Optional.of(links.type()))),
                Optional.empty(),
                1,
                List.of(new CoreField(0, links.type())),
                List.of(),
                links.next(),
                List.of(
                    new CoreConformance(links.type(), List.of(callableWitness, intrinsicWitness)))),
            new CoreDefinition.Enum(
                nominal("Enum"),
                List.of(new CoreTypeParameter(0, Optional.of(links.type()))),
                List.of(new CoreEnumVariant("Value", List.of(new CoreField(0, links.type()))))),
            new CoreDefinition.Interface(
                nominal("Interface"),
                List.of(new CoreTypeParameter(0, Optional.of(links.type()))),
                List.of(links.type()),
                List.of(links.next())),
            new CoreDefinition.InterfaceMethod(
                "method",
                links.type(),
                List.of(new CoreTypeParameter(0, Optional.of(links.type()))),
                List.of(links.type()),
                links.type()),
            new CoreDefinition.BuiltinConformance(
                List.of(new CoreTypeParameter(0, Optional.of(links.type()))),
                new CoreType.Declared(
                    new CoreTypeConstructor.Builtin(new BuiltinTypeId("test.Builtin")),
                    List.of(new CoreType.Parameter(0, CoreNullability.NON_NULL)),
                    CoreValueCategory.VALUE,
                    CoreNullability.NON_NULL),
                links.type(),
                List.of(callableWitness, intrinsicWitness))));
    return List.copyOf(definitions);
  }

  private static List<CoreExpression> expressions(Links links) {
    List<CoreExpression> values = new ArrayList<>();
    values.add(new CoreExpression.Literal(1, 1, links.type()));
    values.add(new CoreExpression.NullLiteral(2, links.type()));
    values.add(
        new CoreExpression.CollectionLiteral(
            3,
            List.of(new CoreExpression.Literal(30, 1, links.type())),
            intrinsic(),
            runtimeType(links),
            links.type()));
    values.add(new CoreExpression.LocalRead(4, 3, links.type()));
    values.add(
        new CoreExpression.FieldRead(
            5, values.get(0), new CoreFieldReference(links.next(), 0), false, links.type()));
    values.add(
        new CoreExpression.EnumConstruct(
            6,
            links.next(),
            "Value",
            runtimeType(links),
            List.of(new CoreArgument(values.get(0), 0)),
            links.type()));
    values.add(new CoreExpression.Unary(7, CoreUnaryOperator.NEGATE, values.get(0), links.type()));
    values.add(
        new CoreExpression.Binary(
            8, values.get(0), CoreBinaryOperator.ADD, values.get(1), links.type()));
    values.add(
        new CoreExpression.Switch(
            9,
            values.get(0),
            List.of(
                new CoreSwitchCase(
                    new CorePattern.Variant(
                        "Value",
                        List.of(
                            new CorePattern.Binding(3, links.type()),
                            CorePattern.Wildcard.INSTANCE,
                            new CorePattern.Literal(1, links.type()),
                            CorePattern.Null.INSTANCE)),
                    block(values.get(1)))),
            links.type()));
    values.add(
        new CoreExpression.Index(
            10, values.get(0), values.get(1), intrinsic(), Optional.of(intrinsic()), links.type()));
    values.add(new CoreExpression.CopyObject(11, values.get(0), false, links.type()));
    values.add(
        new CoreExpression.Closure(
            12,
            links.next(),
            Optional.of(values.get(0)),
            List.of(values.get(1)),
            List.of(runtimeType(links)),
            new CoreType.Function(
                links.type(),
                List.of(new CoreType.Parameter(0, CoreNullability.NON_NULL)),
                CoreNullability.NON_NULL)));
    values.add(
        new CoreExpression.Invoke(
            13, values.get(11), List.of(new CoreArgument(values.get(0), 0)), links.type()));
    values.add(
        new CoreExpression.Call(
            14,
            links.next(),
            Optional.of(values.get(0)),
            List.of(new CoreArgument(values.get(1), 0)),
            List.of(runtimeType(links)),
            false,
            links.type()));
    values.add(
        new CoreExpression.InterfaceCall(
            15,
            links.next(),
            values.get(0),
            List.of(new CoreArgument(values.get(1), 0)),
            List.of(runtimeType(links)),
            false,
            links.type()));
    values.add(
        new CoreExpression.Construct(
            16,
            links.next(),
            links.next(),
            runtimeType(links),
            List.of(new CoreArgument(values.get(0), 0)),
            links.type()));
    values.add(
        new CoreExpression.Intrinsic(
            17,
            intrinsic(),
            Optional.of(values.get(0)),
            List.of(new CoreArgument(values.get(1), 0)),
            Optional.of(runtimeType(links)),
            false,
            links.type()));
    values.add(new CoreExpression.AddressLocal(18, 3, new CoreType.Reference(links.type())));
    values.add(
        new CoreExpression.AddressField(
            19,
            values.get(0),
            new CoreFieldReference(links.next(), 0),
            new CoreType.Reference(links.type())));
    values.add(new CoreExpression.Dereference(20, values.get(17), links.type()));
    return List.copyOf(values);
  }

  private static CoreRuntimeType runtimeType(Links links) {
    return new CoreRuntimeType(links.type(), List.of(new CoreTypeCapture(0, 2)));
  }

  private static CoreBlock block(CoreExpression expression) {
    return new CoreBlock(
        expression.nodeIndex(),
        List.of(new CoreStatement.ExpressionStatement(expression.nodeIndex(), expression)));
  }

  private static IntrinsicId intrinsic() {
    return IntrinsicId.values()[0];
  }

  private static CoreNominalTypeKey nominal(String name) {
    return new CoreNominalTypeKey(
        new ModuleCoordinate("test", 1), "test", name, CoreVisibility.PUBLIC, Optional.empty());
  }

  private static DefinitionReference external(CoreDefinitionLink link) {
    PendingDefinitionReference pending = (PendingDefinitionReference) link;
    return new DefinitionReference.External(
        new DefinitionId(
            DefinitionHasher.hashGroup(
                new byte[] {
                  (byte) (pending.declarationIndex() >>> 24),
                  (byte) (pending.declarationIndex() >>> 16),
                  (byte) (pending.declarationIndex() >>> 8),
                  (byte) pending.declarationIndex()
                }),
            0));
  }

  private static void collectVariants(
      Object value, Set<Class<?>> encountered, IdentityHashMap<Object, Boolean> visited) {
    if (value == null || visited.put(value, Boolean.TRUE) != null) return;
    if (value instanceof Optional<?> optional) {
      optional.ifPresent(item -> collectVariants(item, encountered, visited));
      return;
    }
    if (value instanceof Iterable<?> iterable) {
      iterable.forEach(item -> collectVariants(item, encountered, visited));
      return;
    }
    Class<?> type = value.getClass();
    SEALED_NODES.stream()
        .filter(node -> node.isAssignableFrom(type))
        .forEach(ignored -> encountered.add(type));
    if (!type.isRecord()) return;
    for (RecordComponent component : type.getRecordComponents()) {
      try {
        collectVariants(component.getAccessor().invoke(value), encountered, visited);
      } catch (ReflectiveOperationException exception) {
        throw new AssertionError(exception);
      }
    }
  }

  private static Set<Class<?>> intersection(Class<?> node, Set<Class<?>> encountered) {
    Set<Class<?>> result = new HashSet<>();
    encountered.stream().filter(node::isAssignableFrom).forEach(result::add);
    return Set.copyOf(result);
  }

  private static final class Links {
    private int next;

    private PendingDefinitionReference next() {
      return new PendingDefinitionReference(next++);
    }

    private CoreType type() {
      return new CoreType.Declared(
          new CoreTypeConstructor.User(next()),
          List.of(),
          CoreValueCategory.IDENTITY,
          CoreNullability.NON_NULL);
    }
  }
}
