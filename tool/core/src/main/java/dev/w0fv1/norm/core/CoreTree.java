package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class CoreTree {
  private CoreTree() {}

  static List<CoreDefinitionLink> links(CoreDefinition definition) {
    List<CoreDefinitionLink> result = new ArrayList<>();
    switch (definition) {
      case CoreDefinition.Callable callable -> {
        callable.receiverType().ifPresent(type -> collect(type, result));
        callable.typeParameters().forEach(parameter -> collect(parameter, result));
        callable.parameterTypes().forEach(type -> collect(type, result));
        collect(callable.returnType(), result);
        callable.locals().forEach(local -> collect(local.type(), result));
        collectLinks(callable.body(), result);
      }
      case CoreDefinition.Class classDefinition -> {
        classDefinition.typeParameters().forEach(parameter -> collect(parameter, result));
        classDefinition.fields().forEach(field -> collect(field.type(), result));
        classDefinition.conformances().forEach(conformance -> collect(conformance, result));
      }
      case CoreDefinition.Enum enumDefinition -> {
        enumDefinition.typeParameters().forEach(parameter -> collect(parameter, result));
        enumDefinition
            .variants()
            .forEach(variant -> variant.fields().forEach(field -> collect(field.type(), result)));
      }
      case CoreDefinition.Interface declaration -> {
        declaration.typeParameters().forEach(parameter -> collect(parameter, result));
        declaration.directParents().forEach(type -> collect(type, result));
        result.addAll(declaration.declaredMethods());
      }
      case CoreDefinition.InterfaceMethod method -> {
        collect(method.receiverInterfaceType(), result);
        method.typeParameters().forEach(parameter -> collect(parameter, result));
        method.parameterTypes().forEach(type -> collect(type, result));
        collect(method.returnType(), result);
      }
      case CoreDefinition.BuiltinConformance conformance -> {
        conformance.typeParameters().forEach(parameter -> collect(parameter, result));
        collect(conformance.concreteBuiltinType(), result);
        collect(conformance.interfaceType(), result);
        conformance
            .witnesses()
            .forEach(
                witness -> {
                  result.add(witness.requirement());
                  if (witness.implementation() instanceof CoreWitnessTarget.Callable callable) {
                    result.add(callable.definition());
                  }
                });
      }
    }
    return List.copyOf(result);
  }

  static Map<Integer, DefinitionReference> referenceSites(CoreDefinition definition) {
    Map<Integer, DefinitionReference> result = new LinkedHashMap<>();
    referenceLinks(definition)
        .forEach(
            (nodeIndex, link) -> {
              if (!(link instanceof DefinitionReference reference)) {
                throw new IllegalArgumentException("core definition contains a pending reference");
              }
              result.put(nodeIndex, reference);
            });
    return Map.copyOf(result);
  }

  private static Map<Integer, CoreDefinitionLink> referenceLinks(CoreDefinition definition) {
    Map<Integer, CoreDefinitionLink> result = new LinkedHashMap<>();
    if (definition instanceof CoreDefinition.Callable callable) {
      collect(callable.body(), result);
    }
    return Map.copyOf(result);
  }

  static CoreDefinition resolve(
      CoreDefinition definition,
      Function<PendingDefinitionReference, DefinitionReference> resolver) {
    return switch (definition) {
      case CoreDefinition.Callable callable ->
          new CoreDefinition.Callable(
              callable.receiverType().map(type -> resolve(type, resolver)),
              resolveTypeParameters(callable.typeParameters(), resolver),
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
      case CoreDefinition.Class classDefinition ->
          new CoreDefinition.Class(
              classDefinition.nominalType(),
              resolveTypeParameters(classDefinition.typeParameters(), resolver),
              classDefinition.fields().stream()
                  .map(field -> new CoreField(field.ordinal(), resolve(field.type(), resolver)))
                  .toList(),
              classDefinition.conformances().stream()
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

  private static void collectLinks(CoreBlock block, List<CoreDefinitionLink> result) {
    Map<Integer, CoreDefinitionLink> references = new LinkedHashMap<>();
    collect(block, references);
    result.addAll(references.values());
    collectTypes(block, result);
  }

  private static void collect(CoreType type, List<CoreDefinitionLink> result) {
    result.addAll(CoreTypes.links(type));
  }

  private static void collect(CoreRuntimeType type, List<CoreDefinitionLink> result) {
    collect(type.template(), result);
  }

  private static void collectTypes(CoreBlock block, List<CoreDefinitionLink> result) {
    block.statements().forEach(statement -> collectTypes(statement, result));
  }

  private static void collectTypes(CoreStatement statement, List<CoreDefinitionLink> result) {
    switch (statement) {
      case CoreStatement.LocalDeclaration local -> collectTypes(local.initializer(), result);
      case CoreStatement.LocalAssignment assignment -> collectTypes(assignment.value(), result);
      case CoreStatement.FieldAssignment assignment -> {
        result.add(assignment.field().owner());
        collectTypes(assignment.receiver(), result);
        collectTypes(assignment.value(), result);
      }
      case CoreStatement.IntrinsicAssignment assignment -> {
        collectTypes(assignment.receiver(), result);
        assignment.index().ifPresent(value -> collectTypes(value, result));
        collectTypes(assignment.value(), result);
      }
      case CoreStatement.ExpressionStatement expression ->
          collectTypes(expression.expression(), result);
      case CoreStatement.IfStatement conditional -> {
        collectTypes(conditional.condition(), result);
        collectTypes(conditional.thenBlock(), result);
        collectTypes(conditional.elseBlock(), result);
      }
      case CoreStatement.ConditionalForStatement loop -> {
        collectTypes(loop.condition(), result);
        collectTypes(loop.body(), result);
      }
      case CoreStatement.ForStatement loop -> {
        collectTypes(loop.iterable(), result);
        collect(loop.iteration(), result);
        collectTypes(loop.body(), result);
      }
      case CoreStatement.ReturnStatement returned ->
          returned.value().ifPresent(value -> collectTypes(value, result));
      case CoreStatement.YieldStatement yielded -> collectTypes(yielded.value(), result);
      case CoreStatement.BreakStatement ignored -> {}
      case CoreStatement.ContinueStatement ignored -> {}
    }
  }

  private static void collectTypes(CoreExpression expression, List<CoreDefinitionLink> result) {
    collect(expression.type(), result);
    switch (expression) {
      case CoreExpression.Literal ignored -> {}
      case CoreExpression.NullLiteral ignored -> {}
      case CoreExpression.CollectionLiteral collection -> {
        collect(collection.runtimeType(), result);
        collection.elements().forEach(value -> collectTypes(value, result));
      }
      case CoreExpression.LocalRead ignored -> {}
      case CoreExpression.FieldRead field -> {
        result.add(field.field().owner());
        collectTypes(field.receiver(), result);
      }
      case CoreExpression.EnumConstruct construct -> {
        collect(construct.runtimeType(), result);
        construct.arguments().forEach(argument -> collectTypes(argument.value(), result));
      }
      case CoreExpression.Unary unary -> collectTypes(unary.operand(), result);
      case CoreExpression.Binary binary -> {
        collectTypes(binary.left(), result);
        collectTypes(binary.right(), result);
      }
      case CoreExpression.Switch switched -> {
        collectTypes(switched.value(), result);
        switched.cases().forEach(value -> collectTypes(value.pattern(), result));
        switched.cases().forEach(value -> collectTypes(value.body(), result));
      }
      case CoreExpression.Index index -> {
        collectTypes(index.receiver(), result);
        collectTypes(index.index(), result);
      }
      case CoreExpression.CopyObject copied -> collectTypes(copied.receiver(), result);
      case CoreExpression.Call call -> {
        call.receiver().ifPresent(value -> collectTypes(value, result));
        call.arguments().forEach(argument -> collectTypes(argument.value(), result));
        call.reifiedArguments().forEach(type -> collect(type, result));
      }
      case CoreExpression.InterfaceCall call -> {
        collectTypes(call.receiver(), result);
        call.arguments().forEach(argument -> collectTypes(argument.value(), result));
        call.reifiedArguments().forEach(type -> collect(type, result));
      }
      case CoreExpression.Construct construct -> {
        collect(construct.runtimeType(), result);
        construct.arguments().forEach(argument -> collectTypes(argument.value(), result));
      }
      case CoreExpression.Intrinsic intrinsic -> {
        intrinsic.receiver().ifPresent(value -> collectTypes(value, result));
        intrinsic.arguments().forEach(argument -> collectTypes(argument.value(), result));
        intrinsic.runtimeType().ifPresent(type -> collect(type, result));
      }
    }
  }

  private static void collectTypes(CorePattern pattern, List<CoreDefinitionLink> result) {
    switch (pattern) {
      case CorePattern.Variant variant ->
          variant.arguments().forEach(value -> collectTypes(value, result));
      case CorePattern.Binding binding -> collect(binding.type(), result);
      case CorePattern.Wildcard ignored -> {}
      case CorePattern.Literal literal -> collect(literal.type(), result);
      case CorePattern.Null ignored -> {}
    }
  }

  private static void collect(CoreBlock block, Map<Integer, CoreDefinitionLink> result) {
    block.statements().forEach(statement -> collect(statement, result));
  }

  private static void collect(CoreStatement statement, Map<Integer, CoreDefinitionLink> result) {
    switch (statement) {
      case CoreStatement.LocalDeclaration local -> collect(local.initializer(), result);
      case CoreStatement.LocalAssignment assignment -> collect(assignment.value(), result);
      case CoreStatement.FieldAssignment assignment -> {
        collect(assignment.receiver(), result);
        collect(assignment.value(), result);
      }
      case CoreStatement.IntrinsicAssignment assignment -> {
        collect(assignment.receiver(), result);
        assignment.index().ifPresent(value -> collect(value, result));
        collect(assignment.value(), result);
      }
      case CoreStatement.ExpressionStatement expression -> collect(expression.expression(), result);
      case CoreStatement.IfStatement conditional -> {
        collect(conditional.condition(), result);
        collect(conditional.thenBlock(), result);
        collect(conditional.elseBlock(), result);
      }
      case CoreStatement.ConditionalForStatement loop -> {
        collect(loop.condition(), result);
        collect(loop.body(), result);
      }
      case CoreStatement.ForStatement loop -> {
        collect(loop.iterable(), result);
        collect(loop.body(), result);
      }
      case CoreStatement.ReturnStatement returned ->
          returned.value().ifPresent(value -> collect(value, result));
      case CoreStatement.YieldStatement yielded -> collect(yielded.value(), result);
      case CoreStatement.BreakStatement ignored -> {}
      case CoreStatement.ContinueStatement ignored -> {}
    }
  }

  private static void collect(CoreExpression expression, Map<Integer, CoreDefinitionLink> result) {
    switch (expression) {
      case CoreExpression.Literal ignored -> {}
      case CoreExpression.NullLiteral ignored -> {}
      case CoreExpression.CollectionLiteral collection ->
          collection.elements().forEach(value -> collect(value, result));
      case CoreExpression.LocalRead ignored -> {}
      case CoreExpression.FieldRead field -> collect(field.receiver(), result);
      case CoreExpression.EnumConstruct construct -> {
        put(result, construct.nodeIndex(), construct.target());
        construct.arguments().forEach(argument -> collect(argument.value(), result));
      }
      case CoreExpression.Unary unary -> collect(unary.operand(), result);
      case CoreExpression.Binary binary -> {
        collect(binary.left(), result);
        collect(binary.right(), result);
      }
      case CoreExpression.Switch switched -> {
        collect(switched.value(), result);
        switched.cases().forEach(value -> collect(value.body(), result));
      }
      case CoreExpression.Index index -> {
        collect(index.receiver(), result);
        collect(index.index(), result);
      }
      case CoreExpression.CopyObject copied -> collect(copied.receiver(), result);
      case CoreExpression.Call call -> {
        put(result, call.nodeIndex(), call.target());
        call.receiver().ifPresent(value -> collect(value, result));
        call.arguments().forEach(argument -> collect(argument.value(), result));
      }
      case CoreExpression.InterfaceCall call -> {
        put(result, call.nodeIndex(), call.requirement());
        collect(call.receiver(), result);
        call.arguments().forEach(argument -> collect(argument.value(), result));
      }
      case CoreExpression.Construct construct -> {
        put(result, construct.nodeIndex(), construct.target());
        construct.arguments().forEach(argument -> collect(argument.value(), result));
      }
      case CoreExpression.Intrinsic intrinsic -> {
        intrinsic.receiver().ifPresent(value -> collect(value, result));
        intrinsic.arguments().forEach(argument -> collect(argument.value(), result));
      }
    }
  }

  private static void put(
      Map<Integer, CoreDefinitionLink> references, int nodeIndex, CoreDefinitionLink target) {
    if (references.putIfAbsent(nodeIndex, target) != null) {
      throw new IllegalArgumentException("core reference node index is duplicated");
    }
  }

  private static void collect(CoreConformance conformance, List<CoreDefinitionLink> result) {
    collect(conformance.interfaceType(), result);
    conformance
        .witnesses()
        .forEach(
            witness -> {
              result.add(witness.requirement());
              if (witness.implementation() instanceof CoreWitnessTarget.Callable callable) {
                result.add(callable.definition());
              }
            });
  }

  private static void collect(CoreIteration iteration, List<CoreDefinitionLink> result) {
    if (iteration instanceof CoreIteration.Interface protocol) {
      result.add(protocol.iteratorRequirement());
      result.add(protocol.hasNextRequirement());
      result.add(protocol.nextRequirement());
    }
  }

  private static void collect(CoreTypeParameter parameter, List<CoreDefinitionLink> result) {
    parameter.upperBound().ifPresent(type -> collect(type, result));
  }
}
