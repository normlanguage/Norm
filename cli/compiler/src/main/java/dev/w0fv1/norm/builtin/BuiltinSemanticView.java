package dev.w0fv1.norm.builtin;

import dev.w0fv1.norm.abi.AbiType;
import dev.w0fv1.norm.abi.BuiltinContracts;
import dev.w0fv1.norm.semantic.IndexKind;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.semantic.ValueCategory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

final class BuiltinSemanticView {
  private BuiltinSemanticView() {}

  static SemanticType type(AbiType type) {
    return new SemanticType(
        SemanticType.Kind.valueOf(type.kind().name()),
        type.identity(),
        type.name(),
        type.arguments().stream().map(BuiltinSemanticView::type).toList(),
        ValueCategory.valueOf(type.category().name()),
        type.isNullable() ? SemanticType.Nullability.NULLABLE : SemanticType.Nullability.NON_NULL);
  }

  static ParameterInfo parameter(BuiltinContracts.Parameter parameter) {
    return new ParameterInfo(parameter.name(), type(parameter.type()), parameter.hasDefault());
  }

  static Symbol symbol(BuiltinContracts.Symbol symbol, Optional<String> owner) {
    List<ParameterInfo> parameters =
        symbol.parameters().stream().map(BuiltinSemanticView::parameter).toList();
    String signature =
        parameters.stream()
            .map(parameter -> parameter.type().displayName())
            .collect(Collectors.joining(",", "(", ")"));
    String key =
        switch (symbol.kind()) {
          case TYPE -> "type/" + symbol.name();
          case FUNCTION -> "function/" + symbol.name() + "/" + signature;
          case METHOD, TYPE_METHOD, FIELD ->
              "member/" + owner.orElseThrow() + "/" + symbol.name() + "/" + signature;
        };
    return new Symbol(
        SymbolId.builtin(key),
        symbol.name(),
        SymbolKind.valueOf(symbol.kind().name()),
        type(symbol.type()),
        Optional.empty(),
        owner.map(name -> SymbolId.builtin("type/" + name)),
        symbol.typeParameters().stream()
            .map(
                parameter ->
                    new TypeParameterInfo(
                        parameter.name(),
                        type(parameter.type()),
                        parameter.upperBound().map(BuiltinSemanticView::type),
                        parameter.defaultType().map(BuiltinSemanticView::type)))
            .toList(),
        parameters,
        symbol.documentation());
  }

  static BuiltinCatalog.TypeDefinition definition(BuiltinContracts.TypeDefinition definition) {
    Optional<String> owner = Optional.of(definition.symbol().name());
    return new BuiltinCatalog.TypeDefinition(
        symbol(definition.symbol(), Optional.empty()),
        definition.typeParameters(),
        definition.runtimeShape(),
        definition
            .constructor()
            .map(
                constructor ->
                    new BuiltinCatalog.ConstructorCapability(
                        constructor.parameters().stream()
                            .map(BuiltinSemanticView::parameter)
                            .toList(),
                        constructor.intrinsic())),
        definition.collectionLiteral(),
        definition.defaultCollectionLiteral(),
        definition
            .iterable()
            .map(
                iterable ->
                    new BuiltinCatalog.IterableCapability(
                        type(iterable.elementType()), iterable.intrinsic())),
        definition
            .index()
            .map(
                index ->
                    new BuiltinCatalog.IndexCapability(
                        IndexKind.valueOf(index.kind().name()),
                        type(index.keyType()),
                        type(index.resultType()),
                        index.readIntrinsic(),
                        index.writeIntrinsic())),
        definition.members().stream()
            .map(
                member ->
                    new BuiltinCatalog.MemberDefinition(
                        symbol(member.symbol(), owner),
                        member.intrinsic(),
                        member.writeIntrinsic()))
            .toList(),
        definition.typeMembers().stream()
            .map(
                member ->
                    new BuiltinCatalog.MemberDefinition(
                        symbol(member.symbol(), owner),
                        member.intrinsic(),
                        member.writeIntrinsic()))
            .toList());
  }

  static BuiltinCatalog.ProtocolConformance conformance(
      BuiltinContracts.ProtocolConformance conformance) {
    return new BuiltinCatalog.ProtocolConformance(
        conformance.typeParameters().stream().map(BuiltinSemanticView::type).toList(),
        type(conformance.concreteType()),
        type(conformance.interfaceType()),
        conformance.witnesses().entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    entry ->
                        new BuiltinCatalog.ProtocolWitness(
                            entry.getValue().parameters().stream()
                                .map(BuiltinSemanticView::parameter)
                                .toList(),
                            type(entry.getValue().result()),
                            entry.getValue().intrinsic()))));
  }
}
