package dev.w0fv1.norm.core;

import java.util.List;
import java.util.function.Function;

final class CoreRewriter {
  private CoreRewriter() {}

  static CoreDefinition resolve(
      CoreDefinition definition,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return switch (definition) {
      case CoreDefinition.Callable callable ->
          new CoreDefinition.Callable(
              callable.receiverType().map(type -> resolve(type, resolver)),
              resolveTypeParameters(callable.typeParameters(), resolver),
              callable.captureTypes().stream().map(type -> resolve(type, resolver)).toList(),
              callable.captureLocals(),
              callable.parameterTypes().stream().map(type -> resolve(type, resolver)).toList(),
              callable.parameterLocals(),
              callable.reifiedTypeLocals(),
              resolve(callable.returnType(), resolver),
              callable.locals().stream()
                  .map(
                      local ->
                          new CoreLocal(
                              local.index(), resolve(local.type(), resolver), local.kind()))
                  .toList(),
              resolve(callable.body(), resolver));
      case CoreDefinition.Aggregate aggregateDefinition ->
          new CoreDefinition.Aggregate(
              aggregateDefinition.nominalType(),
              aggregateDefinition.valueCategory(),
              resolveTypeParameters(aggregateDefinition.typeParameters(), resolver),
              aggregateDefinition.fields().stream()
                  .map(field -> new CoreField(field.ordinal(), resolve(field.type(), resolver)))
                  .toList(),
              aggregateDefinition.conformances().stream()
                  .map(value -> resolve(value, resolver))
                  .toList());
      case CoreDefinition.Enum enumDefinition ->
          new CoreDefinition.Enum(
              enumDefinition.nominalType(),
              resolveTypeParameters(enumDefinition.typeParameters(), resolver),
              enumDefinition.variants().stream()
                  .map(
                      variant ->
                          new CoreEnumVariant(
                              variant.key(),
                              variant.fields().stream()
                                  .map(
                                      field ->
                                          new CoreField(
                                              field.ordinal(), resolve(field.type(), resolver)))
                                  .toList()))
                  .toList());
      case CoreDefinition.Interface declaration ->
          new CoreDefinition.Interface(
              declaration.nominalType(),
              resolveTypeParameters(declaration.typeParameters(), resolver),
              declaration.directParents().stream().map(type -> resolve(type, resolver)).toList(),
              declaration.declaredMethods().stream().map(link -> resolve(link, resolver)).toList());
      case CoreDefinition.InterfaceMethod method ->
          new CoreDefinition.InterfaceMethod(
              method.name(),
              resolve(method.receiverInterfaceType(), resolver),
              resolveTypeParameters(method.typeParameters(), resolver),
              method.parameterTypes().stream().map(type -> resolve(type, resolver)).toList(),
              resolve(method.returnType(), resolver));
      case CoreDefinition.BuiltinConformance conformance ->
          new CoreDefinition.BuiltinConformance(
              resolveTypeParameters(conformance.typeParameters(), resolver),
              resolve(conformance.concreteBuiltinType(), resolver),
              resolve(conformance.interfaceType(), resolver),
              conformance.witnesses().stream()
                  .map(
                      witness ->
                          new CoreWitness(
                              resolve(witness.requirement(), resolver),
                              switch (witness.implementation()) {
                                case CoreWitnessTarget.Callable callable ->
                                    new CoreWitnessTarget.Callable(
                                        resolve(callable.definition(), resolver));
                                case CoreWitnessTarget.Intrinsic intrinsic -> intrinsic;
                              }))
                  .toList());
    };
  }

  private static CoreBlock resolve(
      CoreBlock block, Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return new CoreBlock(
        block.nodeIndex(),
        block.statements().stream().map(statement -> resolve(statement, resolver)).toList());
  }

  private static CoreStatement resolve(
      CoreStatement statement, Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return switch (statement) {
      case CoreStatement.LocalDeclaration local ->
          new CoreStatement.LocalDeclaration(
              local.nodeIndex(), local.localIndex(), resolve(local.initializer(), resolver));
      case CoreStatement.LocalAssignment assignment ->
          new CoreStatement.LocalAssignment(
              assignment.nodeIndex(),
              assignment.localIndex(),
              resolve(assignment.value(), resolver));
      case CoreStatement.FieldAssignment assignment ->
          new CoreStatement.FieldAssignment(
              assignment.nodeIndex(),
              resolve(assignment.receiver(), resolver),
              resolve(assignment.field(), resolver),
              resolve(assignment.value(), resolver));
      case CoreStatement.IntrinsicAssignment assignment ->
          new CoreStatement.IntrinsicAssignment(
              assignment.nodeIndex(),
              assignment.intrinsic(),
              resolve(assignment.receiver(), resolver),
              assignment.index().map(value -> resolve(value, resolver)),
              resolve(assignment.value(), resolver));
      case CoreStatement.ExpressionStatement expression ->
          new CoreStatement.ExpressionStatement(
              expression.nodeIndex(), resolve(expression.expression(), resolver));
      case CoreStatement.IfStatement conditional ->
          new CoreStatement.IfStatement(
              conditional.nodeIndex(),
              resolve(conditional.condition(), resolver),
              resolve(conditional.thenBlock(), resolver),
              resolve(conditional.elseBlock(), resolver));
      case CoreStatement.ConditionalForStatement loop ->
          new CoreStatement.ConditionalForStatement(
              loop.nodeIndex(),
              resolve(loop.condition(), resolver),
              resolve(loop.body(), resolver));
      case CoreStatement.ForStatement loop ->
          new CoreStatement.ForStatement(
              loop.nodeIndex(),
              loop.iteratorLocal(),
              loop.variableLocal(),
              loop.indexLocal(),
              resolve(loop.iterable(), resolver),
              resolve(loop.body(), resolver),
              resolve(loop.iteration(), resolver));
      case CoreStatement.ReturnStatement returned ->
          new CoreStatement.ReturnStatement(
              returned.nodeIndex(), returned.value().map(value -> resolve(value, resolver)));
      case CoreStatement.YieldStatement yielded ->
          new CoreStatement.YieldStatement(yielded.nodeIndex(), resolve(yielded.value(), resolver));
      case CoreStatement.BreakStatement broken -> broken;
      case CoreStatement.ContinueStatement continued -> continued;
    };
  }

  private static CoreExpression resolve(
      CoreExpression expression,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return switch (expression) {
      case CoreExpression.Literal literal ->
          new CoreExpression.Literal(
              literal.nodeIndex(), literal.value(), resolve(literal.type(), resolver));
      case CoreExpression.NullLiteral literal ->
          new CoreExpression.NullLiteral(literal.nodeIndex(), resolve(literal.type(), resolver));
      case CoreExpression.CollectionLiteral collection ->
          new CoreExpression.CollectionLiteral(
              collection.nodeIndex(),
              collection.elements().stream().map(value -> resolve(value, resolver)).toList(),
              collection.materializer(),
              resolve(collection.runtimeType(), resolver),
              resolve(collection.type(), resolver));
      case CoreExpression.LocalRead local ->
          new CoreExpression.LocalRead(
              local.nodeIndex(), local.localIndex(), resolve(local.type(), resolver));
      case CoreExpression.FieldRead field ->
          new CoreExpression.FieldRead(
              field.nodeIndex(),
              resolve(field.receiver(), resolver),
              resolve(field.field(), resolver),
              field.nullSafe(),
              resolve(field.type(), resolver));
      case CoreExpression.EnumConstruct construct ->
          new CoreExpression.EnumConstruct(
              construct.nodeIndex(),
              resolve(construct.target(), resolver),
              construct.variantKey(),
              resolve(construct.runtimeType(), resolver),
              resolveArguments(construct.arguments(), resolver),
              resolve(construct.type(), resolver));
      case CoreExpression.Unary unary ->
          new CoreExpression.Unary(
              unary.nodeIndex(),
              unary.operator(),
              resolve(unary.operand(), resolver),
              resolve(unary.type(), resolver));
      case CoreExpression.Binary binary ->
          new CoreExpression.Binary(
              binary.nodeIndex(),
              resolve(binary.left(), resolver),
              binary.operator(),
              resolve(binary.right(), resolver),
              resolve(binary.type(), resolver));
      case CoreExpression.Switch switched ->
          new CoreExpression.Switch(
              switched.nodeIndex(),
              resolve(switched.value(), resolver),
              switched.cases().stream()
                  .map(
                      switchCase ->
                          new CoreSwitchCase(
                              resolve(switchCase.pattern(), resolver),
                              resolve(switchCase.body(), resolver)))
                  .toList(),
              resolve(switched.type(), resolver));
      case CoreExpression.Index index ->
          new CoreExpression.Index(
              index.nodeIndex(),
              resolve(index.receiver(), resolver),
              resolve(index.index(), resolver),
              index.readIntrinsic(),
              index.writeIntrinsic(),
              resolve(index.type(), resolver));
      case CoreExpression.CopyObject copied ->
          new CoreExpression.CopyObject(
              copied.nodeIndex(),
              resolve(copied.receiver(), resolver),
              copied.nullSafe(),
              resolve(copied.type(), resolver));
      case CoreExpression.Closure closure ->
          new CoreExpression.Closure(
              closure.nodeIndex(),
              resolve(closure.target(), resolver),
              closure.receiver().map(value -> resolve(value, resolver)),
              closure.captures().stream().map(value -> resolve(value, resolver)).toList(),
              closure.reifiedArguments().stream().map(type -> resolve(type, resolver)).toList(),
              resolve(closure.type(), resolver));
      case CoreExpression.Invoke invoke ->
          new CoreExpression.Invoke(
              invoke.nodeIndex(),
              resolve(invoke.callee(), resolver),
              resolveArguments(invoke.arguments(), resolver),
              resolve(invoke.type(), resolver));
      case CoreExpression.Call call ->
          new CoreExpression.Call(
              call.nodeIndex(),
              resolve(call.target(), resolver),
              call.receiver().map(value -> resolve(value, resolver)),
              resolveArguments(call.arguments(), resolver),
              call.reifiedArguments().stream().map(type -> resolve(type, resolver)).toList(),
              call.nullSafe(),
              resolve(call.type(), resolver));
      case CoreExpression.InterfaceCall call ->
          new CoreExpression.InterfaceCall(
              call.nodeIndex(),
              resolve(call.requirement(), resolver),
              resolve(call.receiver(), resolver),
              resolveArguments(call.arguments(), resolver),
              call.reifiedArguments().stream().map(type -> resolve(type, resolver)).toList(),
              call.nullSafe(),
              resolve(call.type(), resolver));
      case CoreExpression.Construct construct ->
          new CoreExpression.Construct(
              construct.nodeIndex(),
              resolve(construct.target(), resolver),
              resolve(construct.runtimeType(), resolver),
              resolveArguments(construct.arguments(), resolver),
              resolve(construct.type(), resolver));
      case CoreExpression.Intrinsic intrinsic ->
          new CoreExpression.Intrinsic(
              intrinsic.nodeIndex(),
              intrinsic.intrinsic(),
              intrinsic.receiver().map(value -> resolve(value, resolver)),
              resolveArguments(intrinsic.arguments(), resolver),
              intrinsic.runtimeType().map(type -> resolve(type, resolver)),
              intrinsic.nullSafe(),
              resolve(intrinsic.type(), resolver));
    };
  }

  private static CoreIteration resolve(
      CoreIteration iteration, Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return switch (iteration) {
      case CoreIteration.Builtin builtin -> builtin;
      case CoreIteration.Interface protocol ->
          new CoreIteration.Interface(
              resolve(protocol.iteratorRequirement(), resolver),
              resolve(protocol.hasNextRequirement(), resolver),
              resolve(protocol.nextRequirement(), resolver));
    };
  }

  private static CorePattern resolve(
      CorePattern pattern, Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return switch (pattern) {
      case CorePattern.Variant variant ->
          new CorePattern.Variant(
              variant.variantKey(),
              variant.arguments().stream().map(value -> resolve(value, resolver)).toList());
      case CorePattern.Binding binding ->
          new CorePattern.Binding(binding.localIndex(), resolve(binding.type(), resolver));
      case CorePattern.Wildcard wildcard -> wildcard;
      case CorePattern.Literal literal ->
          new CorePattern.Literal(literal.value(), resolve(literal.type(), resolver));
      case CorePattern.Null nil -> nil;
    };
  }

  private static List<CoreArgument> resolveArguments(
      List<CoreArgument> arguments,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return arguments.stream()
        .map(
            argument ->
                new CoreArgument(resolve(argument.value(), resolver), argument.parameterIndex()))
        .toList();
  }

  private static CoreDefinitionLink resolve(
      CoreDefinitionLink link, Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return link instanceof PendingDefinitionReference pending ? resolver.apply(pending) : link;
  }

  private static CoreFieldReference resolve(
      CoreFieldReference field,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return new CoreFieldReference(resolve(field.owner(), resolver), field.ordinal());
  }

  private static CoreConformance resolve(
      CoreConformance conformance,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return new CoreConformance(
        resolve(conformance.interfaceType(), resolver),
        conformance.witnesses().stream()
            .map(
                witness ->
                    new CoreWitness(
                        resolve(witness.requirement(), resolver),
                        switch (witness.implementation()) {
                          case CoreWitnessTarget.Callable callable ->
                              new CoreWitnessTarget.Callable(
                                  resolve(callable.definition(), resolver));
                          case CoreWitnessTarget.Intrinsic intrinsic -> intrinsic;
                        }))
            .toList());
  }

  private static List<CoreTypeParameter> resolveTypeParameters(
      List<CoreTypeParameter> parameters,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return parameters.stream()
        .map(
            parameter ->
                new CoreTypeParameter(
                    parameter.index(), parameter.upperBound().map(type -> resolve(type, resolver))))
        .toList();
  }

  private static CoreType resolve(
      CoreType type, Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return CoreTypes.mapLinks(
        type,
        link ->
            link instanceof PendingDefinitionReference pending ? resolver.apply(pending) : link);
  }

  private static CoreRuntimeType resolve(
      CoreRuntimeType runtimeType,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return new CoreRuntimeType(resolve(runtimeType.template(), resolver), runtimeType.captures());
  }
}
