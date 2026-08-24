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
        writeTypes(writer, callable.parameterTypes(), referenceResolver);
        writeIntegers(writer, callable.parameterLocals());
        writeIntegers(writer, callable.reifiedTypeLocals());
        writeType(writer, callable.returnType(), referenceResolver);
        writer.writeInt(callable.locals().size());
        callable.locals().forEach(local -> writeLocal(writer, local, referenceResolver));
        writeBlock(writer, callable.body(), referenceResolver);
      }
      case CoreDefinition.Class classDefinition -> {
        writer.writeTag("class");
        writeNominalType(writer, classDefinition.nominalType());
        writer.writeInt(classDefinition.typeParameterCount());
        writer.writeInt(classDefinition.fields().size());
        classDefinition
            .fields()
            .forEach(
                field -> {
                  writer.writeInt(field.ordinal());
                  writeType(writer, field.type(), referenceResolver);
                });
      }
      case CoreDefinition.Enum enumDefinition -> {
        writer.writeTag("enum");
        writeNominalType(writer, enumDefinition.nominalType());
        writer.writeInt(enumDefinition.members().size());
        enumDefinition.members().forEach(writer::writeString);
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
        writeExpression(writer, loop.iterable(), referenceResolver);
        writeBlock(writer, loop.body(), referenceResolver);
        writer.writeTag(loop.iterationIntrinsic().name());
      }
      case CoreStatement.ReturnStatement returned -> {
        writer.writeTag("return");
        writeOptionalExpression(writer, returned.value(), referenceResolver);
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
      case CoreExpression.ArrayLiteral array -> {
        writer.writeTag("array-literal").writeInt(array.elements().size());
        array.elements().forEach(value -> writeExpression(writer, value, referenceResolver));
        writeRuntimeType(writer, array.runtimeType(), referenceResolver);
        writeType(writer, array.type(), referenceResolver);
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
      case CoreExpression.EnumMember member -> {
        writer.writeTag("enum-member");
        writeReference(writer, referenceResolver.apply(member.target()));
        writer.writeInt(member.memberOrdinal());
        writeType(writer, member.type(), referenceResolver);
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
      case CoreExpression.Call call -> {
        writer.writeTag("call");
        writeReference(writer, referenceResolver.apply(call.target()));
        writeOptionalExpression(writer, call.receiver(), referenceResolver);
        writeArguments(writer, call.arguments(), referenceResolver);
        writer.writeInt(call.reifiedArguments().size());
        call.reifiedArguments().forEach(type -> writeRuntimeType(writer, type, referenceResolver));
        writer.writeBoolean(call.nullSafe());
        writeType(writer, call.type(), referenceResolver);
      }
      case CoreExpression.Construct construct -> {
        writer.writeTag("construct");
        writeReference(writer, referenceResolver.apply(construct.target()));
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

  private static void writeTypes(
      CanonicalWriter writer,
      List<CoreType> types,
      Function<CoreDefinitionLink, DefinitionReference> referenceResolver) {
    writer.writeInt(types.size());
    types.forEach(type -> writeType(writer, type, referenceResolver));
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
      case CoreType.Parameter parameter ->
          writer
              .writeTag("type-parameter")
              .writeInt(parameter.index())
              .writeTag(parameter.nullability().name());
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
      case Long integer -> writer.writeTag("integer").writeLong(integer);
      case Integer codePoint -> writer.writeTag("code-point").writeInt(codePoint);
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
