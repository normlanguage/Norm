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
              callable.parameters().stream()
                  .map(
                      parameter ->
                          new CoreCallableParameter(
                              parameter.name(),
                              resolve(parameter.type(), resolver),
                              parameter.localIndex(),
                              parameter.interceptors().stream()
                                  .map(interceptor -> resolve(interceptor, resolver))
                                  .toList()))
                  .toList(),
              callable.reifiedTypeLocals(),
              callable.interceptors().stream()
                  .map(interceptor -> resolve(interceptor, resolver))
                  .toList(),
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
              aggregateDefinition.kind(),
              aggregateDefinition.valueCategory(),
              resolveTypeParameters(aggregateDefinition.typeParameters(), resolver),
              aggregateDefinition.parentType().map(type -> resolve(type, resolver)),
              aggregateDefinition.fieldCount(),
              aggregateDefinition.fields().stream()
                  .map(
                      field ->
                          new CoreField(
                              field.name(),
                              field.ordinal(),
                              resolve(field.type(), resolver),
                              field.interceptors().stream()
                                  .map(interceptor -> resolve(interceptor, resolver))
                                  .toList()))
                  .toList(),
              aggregateDefinition.dispatch().stream()
                  .map(
                      dispatch ->
                          new CoreMethodDispatch(
                              resolve(dispatch.slot(), resolver),
                              resolve(dispatch.implementation(), resolver),
                              resolve(dispatch.receiverType(), resolver)))
                  .toList(),
              aggregateDefinition.constructors().stream()
                  .map(constructor -> resolve(constructor, resolver))
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
                                              field.name(),
                                              field.ordinal(),
                                              resolve(field.type(), resolver),
                                              field.interceptors().stream()
                                                  .map(
                                                      interceptor -> resolve(interceptor, resolver))
                                                  .toList()))
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

  private static CoreInterceptor resolve(
      CoreInterceptor interceptor,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return new CoreInterceptor(
        resolve(interceptor.annotation(), resolver),
        interceptor.values().stream()
            .map(value -> resolveAnnotationValue(value, resolver))
            .toList());
  }

  private static CoreAnnotationValue resolveAnnotationValue(
      CoreAnnotationValue value,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return new CoreAnnotationValue(
        resolve(value.type(), resolver), resolveAnnotationContent(value.value(), resolver));
  }

  private static CoreAnnotationValue.Content resolveAnnotationContent(
      CoreAnnotationValue.Content value,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return switch (value) {
      case CoreAnnotationValue.Literal literal -> literal;
      case CoreAnnotationValue.Null ignored -> CoreAnnotationValue.Null.INSTANCE;
      case CoreAnnotationValue.ListValue list ->
          new CoreAnnotationValue.ListValue(
              list.values().stream().map(item -> resolveAnnotationValue(item, resolver)).toList());
      case CoreAnnotationReference.ClassReference classReference ->
          new CoreAnnotationReference.ClassReference(
              resolve(classReference.reflectedType(), resolver));
      case CoreAnnotationReference.CallableReference callable ->
          new CoreAnnotationReference.CallableReference(
              resolve(callable.callable(), resolver),
              callable.receiverTypeArguments().stream()
                  .map(type -> resolve(type, resolver))
                  .toList(),
              callable.reifiedArguments().stream().map(type -> resolve(type, resolver)).toList(),
              callable.virtual());
      case CoreAnnotationReference.FieldReference field ->
          new CoreAnnotationReference.FieldReference(
              field.ordinal(),
              resolve(field.ownerType(), resolver),
              resolve(field.valueType(), resolver));
      case CoreAnnotationReference.EnumReference enumeration -> enumeration;
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
      case CoreStatement.ReferenceAssignment assignment ->
          new CoreStatement.ReferenceAssignment(
              assignment.nodeIndex(),
              resolve(assignment.reference(), resolver),
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
      case CoreStatement.TryStatement tried ->
          new CoreStatement.TryStatement(
              tried.nodeIndex(),
              resolve(tried.body(), resolver),
              tried.catches().stream()
                  .map(
                      clause ->
                          new CoreCatchClause(
                              resolve(clause.type(), resolver),
                              clause.localIndex(),
                              resolve(clause.body(), resolver)))
                  .toList(),
              tried.finallyBlock().map(block -> resolve(block, resolver)));
      case CoreStatement.ThrowStatement thrown ->
          new CoreStatement.ThrowStatement(
              thrown.nodeIndex(), resolve(thrown.exception(), resolver));
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
      case CoreExpression.AddressLocal address ->
          new CoreExpression.AddressLocal(
              address.nodeIndex(), address.localIndex(), resolve(address.type(), resolver));
      case CoreExpression.AddressField address ->
          new CoreExpression.AddressField(
              address.nodeIndex(),
              resolve(address.receiver(), resolver),
              resolve(address.field(), resolver),
              resolve(address.type(), resolver));
      case CoreExpression.Dereference dereference ->
          new CoreExpression.Dereference(
              dereference.nodeIndex(),
              resolve(dereference.reference(), resolver),
              resolve(dereference.type(), resolver));
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
              closure.receiverTypeArguments().stream()
                  .map(type -> resolve(type, resolver))
                  .toList(),
              closure.virtual(),
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
              call.receiverTypeArguments().stream().map(type -> resolve(type, resolver)).toList(),
              call.virtual(),
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
              resolve(construct.initializer(), resolver),
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
                    parameter.index(),
                    parameter.upperBound().map(type -> resolve(type, resolver)),
                    parameter.defaultType().map(type -> resolve(type, resolver))))
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
