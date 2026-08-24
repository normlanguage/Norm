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
        callable.parameterTypes().forEach(type -> collect(type, result));
        collect(callable.returnType(), result);
        callable.locals().forEach(local -> collect(local.type(), result));
        collectLinks(callable.body(), result);
      }
      case CoreDefinition.Class classDefinition ->
          classDefinition.fields().forEach(field -> collect(field.type(), result));
      case CoreDefinition.Enum ignored -> {}
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
              classDefinition.typeParameterCount(),
              classDefinition.fields().stream()
                  .map(field -> new CoreField(field.ordinal(), resolve(field.type(), resolver)))
                  .toList());
      case CoreDefinition.Enum enumDefinition -> enumDefinition;
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
              resolve(loop.iterable(), resolver),
              resolve(loop.body(), resolver),
              loop.iterationIntrinsic());
      case CoreStatement.ReturnStatement returned ->
          new CoreStatement.ReturnStatement(
              returned.nodeIndex(), returned.value().map(value -> resolve(value, resolver)));
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
      case CoreExpression.ArrayLiteral array ->
          new CoreExpression.ArrayLiteral(
              array.nodeIndex(),
              array.elements().stream().map(value -> resolve(value, resolver)).toList(),
              resolve(array.runtimeType(), resolver),
              resolve(array.type(), resolver));
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
      case CoreExpression.EnumMember member ->
          new CoreExpression.EnumMember(
              member.nodeIndex(),
              resolve(member.target(), resolver),
              member.memberOrdinal(),
              resolve(member.type(), resolver));
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
        collectTypes(loop.body(), result);
      }
      case CoreStatement.ReturnStatement returned ->
          returned.value().ifPresent(value -> collectTypes(value, result));
      case CoreStatement.BreakStatement ignored -> {}
      case CoreStatement.ContinueStatement ignored -> {}
    }
  }

  private static void collectTypes(CoreExpression expression, List<CoreDefinitionLink> result) {
    collect(expression.type(), result);
    switch (expression) {
      case CoreExpression.Literal ignored -> {}
      case CoreExpression.NullLiteral ignored -> {}
      case CoreExpression.ArrayLiteral array -> {
        collect(array.runtimeType(), result);
        array.elements().forEach(value -> collectTypes(value, result));
      }
      case CoreExpression.LocalRead ignored -> {}
      case CoreExpression.FieldRead field -> {
        result.add(field.field().owner());
        collectTypes(field.receiver(), result);
      }
      case CoreExpression.EnumMember ignored -> {}
      case CoreExpression.Unary unary -> collectTypes(unary.operand(), result);
      case CoreExpression.Binary binary -> {
        collectTypes(binary.left(), result);
        collectTypes(binary.right(), result);
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
      case CoreStatement.BreakStatement ignored -> {}
      case CoreStatement.ContinueStatement ignored -> {}
    }
  }

  private static void collect(CoreExpression expression, Map<Integer, CoreDefinitionLink> result) {
    switch (expression) {
      case CoreExpression.Literal ignored -> {}
      case CoreExpression.NullLiteral ignored -> {}
      case CoreExpression.ArrayLiteral array ->
          array.elements().forEach(value -> collect(value, result));
      case CoreExpression.LocalRead ignored -> {}
      case CoreExpression.FieldRead field -> collect(field.receiver(), result);
      case CoreExpression.EnumMember member -> put(result, member.nodeIndex(), member.target());
      case CoreExpression.Unary unary -> collect(unary.operand(), result);
      case CoreExpression.Binary binary -> {
        collect(binary.left(), result);
        collect(binary.right(), result);
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
}
