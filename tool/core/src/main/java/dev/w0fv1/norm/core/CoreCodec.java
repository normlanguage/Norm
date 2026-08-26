package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

final class CoreCodec {
  private CoreCodec() {}

  static byte[] encodeGroup(List<CoreDefinition> definitions) {
    CanonicalWriter writer =
        new CanonicalWriter().writeTag("core-group").writeInt(definitions.size());
    definitions.forEach(
        definition -> writeDefinition(writer, definition, CoreCodec::requireResolved));
    return writer.toByteArray();
  }

  static byte[] encodeDefinition(
      CoreDefinition definition,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    CanonicalWriter writer = new CanonicalWriter();
    writeDefinition(
        writer,
        Objects.requireNonNull(definition, "definition"),
        Objects.requireNonNull(referenceResolver, "referenceResolver"));
    return writer.toByteArray();
  }

  private static void writeDefinition(
      CanonicalWriter writer,
      CoreDefinition definition,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    switch (definition) {
      case CoreDefinition.Callable callable -> {
        writer.writeTag("callable").writeBoolean(callable.receiverType().isPresent());
        callable.receiverType().ifPresent(type -> writeType(writer, type, referenceResolver));
        writeTypeParameters(writer, callable.typeParameters(), referenceResolver);
        writeTypes(writer, callable.captureTypes(), referenceResolver);
        writeIntegers(writer, callable.captureLocals());
        writeTypes(writer, callable.parameterTypes(), referenceResolver);
        writeIntegers(writer, callable.parameterLocals());
        writeIntegers(writer, callable.reifiedTypeLocals());
        writeType(writer, callable.returnType(), referenceResolver);
        writer.writeInt(callable.locals().size());
        callable.locals().forEach(local -> writeLocal(writer, local, referenceResolver));
        writeBlock(writer, callable.body(), referenceResolver);
      }
      case CoreDefinition.Aggregate aggregateDefinition -> {
        writer.writeTag("aggregate");
        writeNominalType(writer, aggregateDefinition.nominalType());
        writer.writeTag(aggregateDefinition.valueCategory().name());
        writeTypeParameters(writer, aggregateDefinition.typeParameters(), referenceResolver);
        writer.writeBoolean(aggregateDefinition.parentType().isPresent());
        aggregateDefinition
            .parentType()
            .ifPresent(type -> writeType(writer, type, referenceResolver));
        writer.writeInt(aggregateDefinition.fieldCount());
        writer.writeInt(aggregateDefinition.fields().size());
        aggregateDefinition
            .fields()
            .forEach(
                field -> {
                  writer.writeInt(field.ordinal());
                  writeType(writer, field.type(), referenceResolver);
                });
        writer.writeInt(aggregateDefinition.dispatch().size());
        aggregateDefinition.dispatch().stream()
            .sorted(
                java.util.Comparator.comparing(
                    dispatch ->
                        java.util.HexFormat.of()
                            .formatHex(referenceBytes(referenceResolver.apply(dispatch.slot())))))
            .forEach(
                dispatch -> {
                  writeReference(writer, referenceResolver.apply(dispatch.slot()));
                  writeReference(writer, referenceResolver.apply(dispatch.implementation()));
                  writeType(writer, dispatch.receiverType(), referenceResolver);
                });
        writeReference(writer, referenceResolver.apply(aggregateDefinition.constructor()));
        writer.writeInt(aggregateDefinition.conformances().size());
        aggregateDefinition.conformances().stream()
            .sorted(
                (left, right) ->
                    java.util.Arrays.compareUnsigned(
                        conformanceKey(left, referenceResolver),
                        conformanceKey(right, referenceResolver)))
            .forEach(value -> writeConformance(writer, value, referenceResolver));
      }
      case CoreDefinition.Enum enumDefinition -> {
        writer.writeTag("enum");
        writeNominalType(writer, enumDefinition.nominalType());
        writeTypeParameters(writer, enumDefinition.typeParameters(), referenceResolver);
        writer.writeInt(enumDefinition.variants().size());
        enumDefinition
            .variants()
            .forEach(
                variant -> {
                  writer.writeString(variant.key()).writeInt(variant.fields().size());
                  variant
                      .fields()
                      .forEach(
                          field -> {
                            writer.writeInt(field.ordinal());
                            writeType(writer, field.type(), referenceResolver);
                          });
                });
      }
      case CoreDefinition.Interface declaration -> {
        writer.writeTag("interface");
        writeNominalType(writer, declaration.nominalType());
        writeTypeParameters(writer, declaration.typeParameters(), referenceResolver);
        writer.writeInt(declaration.directParents().size());
        declaration.directParents().stream()
            .sorted(
                (left, right) ->
                    java.util.Arrays.compareUnsigned(
                        typeKey(left, referenceResolver), typeKey(right, referenceResolver)))
            .forEach(type -> writeType(writer, type, referenceResolver));
        writer.writeInt(declaration.declaredMethods().size());
        declaration.declaredMethods().stream()
            .sorted(
                (left, right) ->
                    java.util.Arrays.compareUnsigned(
                        referenceBytes(referenceResolver.apply(left)),
                        referenceBytes(referenceResolver.apply(right))))
            .forEach(link -> writeReference(writer, referenceResolver.apply(link)));
      }
      case CoreDefinition.InterfaceMethod method -> {
        writer.writeTag("interface-method").writeString(method.name());
        writeType(writer, method.receiverInterfaceType(), referenceResolver);
        writeTypeParameters(writer, method.typeParameters(), referenceResolver);
        writeTypes(writer, method.parameterTypes(), referenceResolver);
        writeType(writer, method.returnType(), referenceResolver);
      }
      case CoreDefinition.BuiltinConformance conformance -> {
        writer.writeTag("builtin-conformance");
        writeTypeParameters(writer, conformance.typeParameters(), referenceResolver);
        writeType(writer, conformance.concreteBuiltinType(), referenceResolver);
        writeType(writer, conformance.interfaceType(), referenceResolver);
        writer.writeInt(conformance.witnesses().size());
        conformance.witnesses().stream()
            .sorted(
                java.util.Comparator.comparing(
                    witness ->
                        java.util.HexFormat.of()
                            .formatHex(
                                referenceBytes(referenceResolver.apply(witness.requirement())))))
            .forEach(
                witness -> {
                  writeReference(writer, referenceResolver.apply(witness.requirement()));
                  switch (witness.implementation()) {
                    case CoreWitnessTarget.Callable callable -> {
                      writer.writeTag("callable-witness");
                      writeReference(writer, referenceResolver.apply(callable.definition()));
                    }
                    case CoreWitnessTarget.Intrinsic intrinsic ->
                        writer.writeTag("intrinsic-witness").writeTag(intrinsic.intrinsic().name());
                  }
                });
      }
    }
  }

  private static void writeLocal(
      CanonicalWriter writer,
      CoreLocal local,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writer.writeInt(local.index()).writeTag(local.kind().name());
    writeType(writer, local.type(), referenceResolver);
  }

  private static void writeBlock(
      CanonicalWriter writer,
      CoreBlock block,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writer.writeTag("block").writeInt(block.statements().size());
    block.statements().forEach(statement -> writeStatement(writer, statement, referenceResolver));
  }

  private static void writeStatement(
      CanonicalWriter writer,
      CoreStatement statement,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    switch (statement) {
      case CoreStatement.LocalDeclaration local -> {
        writer.writeTag("local-declaration").writeInt(local.localIndex());
        writeExpression(writer, local.initializer(), referenceResolver);
      }
      case CoreStatement.LocalAssignment assignment -> {
        writer.writeTag("local-assignment").writeInt(assignment.localIndex());
        writeExpression(writer, assignment.value(), referenceResolver);
      }
      case CoreStatement.FieldAssignment assignment -> {
        writer.writeTag("field-assignment");
        writeExpression(writer, assignment.receiver(), referenceResolver);
        writeField(writer, assignment.field(), referenceResolver);
        writeExpression(writer, assignment.value(), referenceResolver);
      }
      case CoreStatement.IntrinsicAssignment assignment -> {
        writer.writeTag("intrinsic-assignment").writeTag(assignment.intrinsic().name());
        writeExpression(writer, assignment.receiver(), referenceResolver);
        writeOptionalExpression(writer, assignment.index(), referenceResolver);
        writeExpression(writer, assignment.value(), referenceResolver);
      }
      case CoreStatement.ReferenceAssignment assignment -> {
        writer.writeTag("reference-assignment");
        writeExpression(writer, assignment.reference(), referenceResolver);
        writeExpression(writer, assignment.value(), referenceResolver);
      }
      case CoreStatement.ExpressionStatement expression -> {
        writer.writeTag("expression-statement");
        writeExpression(writer, expression.expression(), referenceResolver);
      }
      case CoreStatement.IfStatement conditional -> {
        writer.writeTag("if");
        writeExpression(writer, conditional.condition(), referenceResolver);
        writeBlock(writer, conditional.thenBlock(), referenceResolver);
        writeBlock(writer, conditional.elseBlock(), referenceResolver);
      }
      case CoreStatement.ConditionalForStatement loop -> {
        writer.writeTag("conditional-for");
        writeExpression(writer, loop.condition(), referenceResolver);
        writeBlock(writer, loop.body(), referenceResolver);
      }
      case CoreStatement.ForStatement loop -> {
        writer.writeTag("for").writeInt(loop.iteratorLocal()).writeInt(loop.variableLocal());
        writer.writeBoolean(loop.indexLocal().isPresent());
        loop.indexLocal().ifPresent(writer::writeInt);
        writeExpression(writer, loop.iterable(), referenceResolver);
        writeBlock(writer, loop.body(), referenceResolver);
        switch (loop.iteration()) {
          case CoreIteration.Builtin builtin ->
              writer.writeTag("builtin").writeTag(builtin.intrinsic().name());
          case CoreIteration.Interface protocol -> {
            writer.writeTag("interface");
            writeReference(writer, referenceResolver.apply(protocol.iteratorRequirement()));
            writeReference(writer, referenceResolver.apply(protocol.hasNextRequirement()));
            writeReference(writer, referenceResolver.apply(protocol.nextRequirement()));
          }
        }
      }
      case CoreStatement.ReturnStatement returned -> {
        writer.writeTag("return");
        writeOptionalExpression(writer, returned.value(), referenceResolver);
      }
      case CoreStatement.YieldStatement yielded -> {
        writer.writeTag("yield");
        writeExpression(writer, yielded.value(), referenceResolver);
      }
      case CoreStatement.BreakStatement ignored -> writer.writeTag("break");
      case CoreStatement.ContinueStatement ignored -> writer.writeTag("continue");
    }
  }

  private static void writeExpression(
      CanonicalWriter writer,
      CoreExpression expression,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    switch (expression) {
      case CoreExpression.Literal literal -> {
        writer.writeTag("literal");
        writeLiteral(writer, literal.value());
        writeType(writer, literal.type(), referenceResolver);
      }
      case CoreExpression.NullLiteral literal -> {
        writer.writeTag("null");
        writeType(writer, literal.type(), referenceResolver);
      }
      case CoreExpression.CollectionLiteral collection -> {
        writer
            .writeTag("collection-literal")
            .writeTag(collection.materializer().name())
            .writeInt(collection.elements().size());
        collection.elements().forEach(value -> writeExpression(writer, value, referenceResolver));
        writeRuntimeType(writer, collection.runtimeType(), referenceResolver);
        writeType(writer, collection.type(), referenceResolver);
      }
      case CoreExpression.LocalRead local -> {
        writer.writeTag("local-read").writeInt(local.localIndex());
        writeType(writer, local.type(), referenceResolver);
      }
      case CoreExpression.FieldRead field -> {
        writer.writeTag("field-read");
        writeExpression(writer, field.receiver(), referenceResolver);
        writeField(writer, field.field(), referenceResolver);
        writer.writeBoolean(field.nullSafe());
        writeType(writer, field.type(), referenceResolver);
      }
      case CoreExpression.AddressLocal address -> {
        writer.writeTag("address-local").writeInt(address.localIndex());
        writeType(writer, address.type(), referenceResolver);
      }
      case CoreExpression.AddressField address -> {
        writer.writeTag("address-field");
        writeExpression(writer, address.receiver(), referenceResolver);
        writeField(writer, address.field(), referenceResolver);
        writeType(writer, address.type(), referenceResolver);
      }
      case CoreExpression.Dereference dereference -> {
        writer.writeTag("dereference");
        writeExpression(writer, dereference.reference(), referenceResolver);
        writeType(writer, dereference.type(), referenceResolver);
      }
      case CoreExpression.EnumConstruct construct -> {
        writer.writeTag("enum-construct");
        writeReference(writer, referenceResolver.apply(construct.target()));
        writer.writeString(construct.variantKey());
        writeRuntimeType(writer, construct.runtimeType(), referenceResolver);
        writeArguments(writer, construct.arguments(), referenceResolver);
        writeType(writer, construct.type(), referenceResolver);
      }
      case CoreExpression.Unary unary -> {
        writer.writeTag("unary").writeTag(unary.operator().name());
        writeExpression(writer, unary.operand(), referenceResolver);
        writeType(writer, unary.type(), referenceResolver);
      }
      case CoreExpression.Binary binary -> {
        writer.writeTag("binary").writeTag(binary.operator().name());
        writeExpression(writer, binary.left(), referenceResolver);
        writeExpression(writer, binary.right(), referenceResolver);
        writeType(writer, binary.type(), referenceResolver);
      }
      case CoreExpression.Switch switched -> {
        writer.writeTag("switch");
        writeExpression(writer, switched.value(), referenceResolver);
        writer.writeInt(switched.cases().size());
        switched.cases().forEach(value -> writeSwitchCase(writer, value, referenceResolver));
        writeType(writer, switched.type(), referenceResolver);
      }
      case CoreExpression.Index index -> {
        writer.writeTag("index");
        writeExpression(writer, index.receiver(), referenceResolver);
        writeExpression(writer, index.index(), referenceResolver);
        writer.writeTag(index.readIntrinsic().name());
        writeOptionalTag(writer, index.writeIntrinsic().map(java.lang.Enum::name));
        writeType(writer, index.type(), referenceResolver);
      }
      case CoreExpression.CopyObject copied -> {
        writer.writeTag("copy-object");
        writeExpression(writer, copied.receiver(), referenceResolver);
        writer.writeBoolean(copied.nullSafe());
        writeType(writer, copied.type(), referenceResolver);
      }
      case CoreExpression.Closure closure -> {
        writer.writeTag("closure");
        writeReference(writer, referenceResolver.apply(closure.target()));
        writeOptionalExpression(writer, closure.receiver(), referenceResolver);
        writer.writeInt(closure.captures().size());
        closure.captures().forEach(value -> writeExpression(writer, value, referenceResolver));
        writer.writeInt(closure.reifiedArguments().size());
        closure
            .reifiedArguments()
            .forEach(type -> writeRuntimeType(writer, type, referenceResolver));
        writer.writeInt(closure.receiverTypeArguments().size());
        closure
            .receiverTypeArguments()
            .forEach(type -> writeRuntimeType(writer, type, referenceResolver));
        writer.writeBoolean(closure.virtual());
        writeType(writer, closure.type(), referenceResolver);
      }
      case CoreExpression.Invoke invoke -> {
        writer.writeTag("invoke");
        writeExpression(writer, invoke.callee(), referenceResolver);
        writeArguments(writer, invoke.arguments(), referenceResolver);
        writeType(writer, invoke.type(), referenceResolver);
      }
      case CoreExpression.Call call -> {
        writer.writeTag("call");
        writeReference(writer, referenceResolver.apply(call.target()));
        writeOptionalExpression(writer, call.receiver(), referenceResolver);
        writeArguments(writer, call.arguments(), referenceResolver);
        writer.writeInt(call.reifiedArguments().size());
        call.reifiedArguments().forEach(type -> writeRuntimeType(writer, type, referenceResolver));
        writer.writeInt(call.receiverTypeArguments().size());
        call.receiverTypeArguments()
            .forEach(type -> writeRuntimeType(writer, type, referenceResolver));
        writer.writeBoolean(call.virtual());
        writer.writeBoolean(call.nullSafe());
        writeType(writer, call.type(), referenceResolver);
      }
      case CoreExpression.InterfaceCall call -> {
        writer.writeTag("interface-call");
        writeReference(writer, referenceResolver.apply(call.requirement()));
        writeExpression(writer, call.receiver(), referenceResolver);
        writeArguments(writer, call.arguments(), referenceResolver);
        writer.writeInt(call.reifiedArguments().size());
        call.reifiedArguments().forEach(type -> writeRuntimeType(writer, type, referenceResolver));
        writer.writeBoolean(call.nullSafe());
        writeType(writer, call.type(), referenceResolver);
      }
      case CoreExpression.Construct construct -> {
        writer.writeTag("construct");
        writeReference(writer, referenceResolver.apply(construct.target()));
        writeReference(writer, referenceResolver.apply(construct.initializer()));
        writeRuntimeType(writer, construct.runtimeType(), referenceResolver);
        writeArguments(writer, construct.arguments(), referenceResolver);
        writeType(writer, construct.type(), referenceResolver);
      }
      case CoreExpression.Intrinsic intrinsic -> {
        writer.writeTag("intrinsic").writeTag(intrinsic.intrinsic().name());
        writeOptionalExpression(writer, intrinsic.receiver(), referenceResolver);
        writeArguments(writer, intrinsic.arguments(), referenceResolver);
        writer.writeBoolean(intrinsic.runtimeType().isPresent());
        intrinsic
            .runtimeType()
            .ifPresent(type -> writeRuntimeType(writer, type, referenceResolver));
        writer.writeBoolean(intrinsic.nullSafe());
        writeType(writer, intrinsic.type(), referenceResolver);
      }
    }
  }

  private static void writeSwitchCase(
      CanonicalWriter writer,
      CoreSwitchCase switchCase,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writePattern(writer, switchCase.pattern(), referenceResolver);
    writeBlock(writer, switchCase.body(), referenceResolver);
  }

  private static void writePattern(
      CanonicalWriter writer,
      CorePattern pattern,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    switch (pattern) {
      case CorePattern.Variant variant -> {
        writer.writeTag("variant-pattern").writeString(variant.variantKey());
        writer.writeInt(variant.arguments().size());
        variant.arguments().forEach(value -> writePattern(writer, value, referenceResolver));
      }
      case CorePattern.Binding binding -> {
        writer.writeTag("binding-pattern").writeInt(binding.localIndex());
        writeType(writer, binding.type(), referenceResolver);
      }
      case CorePattern.Wildcard ignored -> writer.writeTag("wildcard-pattern");
      case CorePattern.Literal literal -> {
        writer.writeTag("literal-pattern");
        writeLiteral(writer, literal.value());
        writeType(writer, literal.type(), referenceResolver);
      }
      case CorePattern.Null ignored -> writer.writeTag("null-pattern");
    }
  }

  private static void writeArguments(
      CanonicalWriter writer,
      List<CoreArgument> arguments,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writer.writeInt(arguments.size());
    arguments.forEach(
        argument -> {
          writeExpression(writer, argument.value(), referenceResolver);
          writer.writeInt(argument.parameterIndex());
        });
  }

  private static void writeRuntimeType(
      CanonicalWriter writer,
      CoreRuntimeType runtimeType,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writeType(writer, runtimeType.template(), referenceResolver);
    writer.writeInt(runtimeType.captures().size());
    runtimeType
        .captures()
        .forEach(
            capture ->
                writer.writeInt(capture.typeParameterIndex()).writeInt(capture.localIndex()));
  }

  private static void writeConformance(
      CanonicalWriter writer,
      CoreConformance conformance,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writeType(writer, conformance.interfaceType(), referenceResolver);
    writer.writeInt(conformance.witnesses().size());
    conformance.witnesses().stream()
        .sorted(
            java.util.Comparator.comparing(
                witness ->
                    java.util.HexFormat.of()
                        .formatHex(referenceBytes(referenceResolver.apply(witness.requirement())))))
        .forEach(
            witness -> {
              writeReference(writer, referenceResolver.apply(witness.requirement()));
              switch (witness.implementation()) {
                case CoreWitnessTarget.Callable callable -> {
                  writer.writeTag("callable-witness");
                  writeReference(writer, referenceResolver.apply(callable.definition()));
                }
                case CoreWitnessTarget.Intrinsic intrinsic ->
                    writer.writeTag("intrinsic-witness").writeTag(intrinsic.intrinsic().name());
              }
            });
  }

  private static byte[] referenceBytes(DefinitionReference reference) {
    CanonicalWriter writer = new CanonicalWriter();
    writeReference(writer, reference);
    return writer.toByteArray();
  }

  private static byte[] conformanceKey(
      CoreConformance conformance,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    return typeKey(conformance.interfaceType(), referenceResolver);
  }

  private static byte[] typeKey(
      CoreType type, Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    CanonicalWriter writer = new CanonicalWriter();
    writeType(writer, type, referenceResolver);
    return writer.toByteArray();
  }

  private static void writeTypes(
      CanonicalWriter writer,
      List<CoreType> types,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writer.writeInt(types.size());
    types.forEach(type -> writeType(writer, type, referenceResolver));
  }

  private static void writeTypeParameters(
      CanonicalWriter writer,
      List<CoreTypeParameter> parameters,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writer.writeInt(parameters.size());
    parameters.forEach(
        parameter -> {
          writer.writeInt(parameter.index()).writeBoolean(parameter.upperBound().isPresent());
          parameter.upperBound().ifPresent(type -> writeType(writer, type, referenceResolver));
        });
  }

  static void writeType(CanonicalWriter writer, CoreType type) {
    writeType(writer, type, CoreCodec::requireResolved);
  }

  private static void writeType(
      CanonicalWriter writer,
      CoreType type,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    switch (type) {
      case CoreType.Declared declared -> {
        writer.writeTag("declared-type");
        switch (declared.constructor()) {
          case CoreTypeConstructor.Builtin builtin ->
              writer.writeTag("builtin-type").writeString(builtin.id().value());
          case CoreTypeConstructor.User user -> {
            writer.writeTag("user-type");
            writeReference(writer, referenceResolver.apply(user.definition()));
          }
        }
        writer.writeTag(declared.category().name()).writeTag(declared.nullability().name());
        writeTypes(writer, declared.arguments(), referenceResolver);
      }
      case CoreType.Function function -> {
        writer.writeTag("function-type").writeTag(function.nullability().name());
        writeType(writer, function.returnType(), referenceResolver);
        writeTypes(writer, function.parameterTypes(), referenceResolver);
      }
      case CoreType.Parameter parameter ->
          writer
              .writeTag("type-parameter")
              .writeInt(parameter.index())
              .writeTag(parameter.nullability().name());
      case CoreType.Reference reference -> {
        writer.writeTag("reference-type");
        writeType(writer, reference.target(), referenceResolver);
      }
      case CoreType.Special special ->
          writer.writeTag("special-type").writeTag(special.kind().name());
    }
  }

  private static void writeNominalType(CanonicalWriter writer, CoreNominalTypeKey nominalType) {
    writer
        .writeTag("nominal-type")
        .writeString(nominalType.module().name())
        .writeInt(nominalType.module().version())
        .writeString(nominalType.packageName())
        .writeString(nominalType.name())
        .writeTag(nominalType.visibility().name())
        .writeBoolean(nominalType.privateSourcePath().isPresent());
    nominalType.privateSourcePath().ifPresent(writer::writeString);
  }

  private static void writeField(
      CanonicalWriter writer,
      CoreFieldReference field,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writeReference(writer, referenceResolver.apply(field.owner()));
    writer.writeInt(field.ordinal());
  }

  private static void writeReference(CanonicalWriter writer, DefinitionReference reference) {
    switch (Objects.requireNonNull(reference, "reference")) {
      case DefinitionReference.External external ->
          writer
              .writeTag("external-definition")
              .writeBytes(external.definition().group().hash().bytes())
              .writeInt(external.definition().memberIndex());
      case DefinitionReference.RecursiveMember recursive ->
          writer.writeTag("recursive-member").writeInt(recursive.memberIndex());
    }
  }

  private static void writeLiteral(CanonicalWriter writer, Object value) {
    switch (value) {
      case Integer integer -> writer.writeTag("integer").writeInt(integer);
      case Long integer -> writer.writeTag("long").writeLong(integer);
      case Float decimal -> writer.writeTag("float").writeInt(Float.floatToRawIntBits(decimal));
      case Double decimal ->
          writer.writeTag("double").writeLong(Double.doubleToRawLongBits(decimal));
      case Boolean bool -> writer.writeTag("boolean").writeBoolean(bool);
      case String string -> writer.writeTag("string").writeString(string);
      default -> throw new IllegalArgumentException("unsupported core literal value");
    }
  }

  private static void writeIntegers(CanonicalWriter writer, List<Integer> values) {
    writer.writeInt(values.size());
    values.forEach(writer::writeInt);
  }

  private static void writeOptionalExpression(
      CanonicalWriter writer,
      Optional<CoreExpression> value,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writer.writeBoolean(value.isPresent());
    value.ifPresent(expression -> writeExpression(writer, expression, referenceResolver));
  }

  private static void writeOptionalTag(CanonicalWriter writer, Optional<String> value) {
    writer.writeBoolean(value.isPresent());
    value.ifPresent(writer::writeTag);
  }

  private static DefinitionReference requireResolved(CoreDefinitionLink link) {
    if (link instanceof DefinitionReference reference) return reference;
    throw new IllegalArgumentException("canonical core contains a pending definition reference");
  }
}
