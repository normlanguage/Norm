package dev.w0fv1.norm.abi;

import java.util.*;

public final class BuiltinContracts {
  private static final BuiltinContracts STANDARD =
      new BuiltinContracts(BuiltinDeclarations.types(), BuiltinDeclarations.globals());
  private final Map<String, TypeDefinition> types;
  private final Map<String, TypeDefinition> allTypes;
  private final Map<String, List<GlobalDefinition>> globals;

  private BuiltinContracts(List<TypeDefinition> declarations, List<GlobalDefinition> functions) {
    Map<String, TypeDefinition> indexed = new LinkedHashMap<>();
    Map<String, TypeDefinition> visible = new LinkedHashMap<>();
    for (TypeDefinition type : declarations) {
      if (indexed.putIfAbsent(type.symbol().name(), type) != null)
        throw new IllegalArgumentException("duplicate ABI type " + type.symbol().name());
      if (!type.hidden()) visible.put(type.symbol().name(), type);
    }
    types = Map.copyOf(visible);
    allTypes = Map.copyOf(indexed);
    Map<String, List<GlobalDefinition>> overloads = new LinkedHashMap<>();
    for (GlobalDefinition function : functions)
      overloads
          .computeIfAbsent(function.symbol().name(), ignored -> new ArrayList<>())
          .add(function);
    Map<String, List<GlobalDefinition>> immutable = new LinkedHashMap<>();
    overloads.forEach((name, values) -> immutable.put(name, List.copyOf(values)));
    globals = Map.copyOf(immutable);
  }

  public static BuiltinContracts standard() {
    return STANDARD;
  }

  public Collection<TypeDefinition> types() {
    return allTypes.values();
  }

  public List<GlobalDefinition> globals() {
    return globals.values().stream().flatMap(List::stream).toList();
  }

  public Optional<TypeDefinition> type(String name) {
    return Optional.ofNullable(allTypes.get(name));
  }

  private static AbiType ownerType(TypeDefinition definition) {
    List<AbiType> arguments =
        definition.symbol().name().equals("Function")
            ? List.of(BuiltinDeclarations.type("std.core.existential"))
            : definition.symbol().typeParameters().stream().map(TypeParameter::type).toList();
    return AbiType.declared(
        definition.symbol().type().identity(),
        definition.symbol().name(),
        arguments,
        definition.symbol().type().category());
  }

  public enum SymbolKind {
    TYPE,
    METHOD,
    TYPE_METHOD,
    FIELD,
    FUNCTION
  }

  public enum IndexKind {
    NONE,
    INTEGER,
    VALUE
  }

  public record Symbol(
      String name,
      SymbolKind kind,
      AbiType type,
      List<TypeParameter> typeParameters,
      List<Parameter> parameters,
      String documentation) {
    public Symbol {
      Objects.requireNonNull(name);
      Objects.requireNonNull(kind);
      Objects.requireNonNull(type);
      Objects.requireNonNull(documentation);
      typeParameters = List.copyOf(typeParameters);
      parameters = List.copyOf(parameters);
    }
  }

  public record Parameter(String name, AbiType type, boolean hasDefault) {
    public Parameter {
      Objects.requireNonNull(name);
      Objects.requireNonNull(type);
    }
  }

  public record TypeParameter(
      String name, AbiType type, Optional<AbiType> upperBound, Optional<AbiType> defaultType) {
    public TypeParameter {
      Objects.requireNonNull(name);
      Objects.requireNonNull(type);
      Objects.requireNonNull(upperBound);
      Objects.requireNonNull(defaultType);
    }
  }

  public record TypeDefinition(
      Symbol symbol,
      RuntimeShape runtimeShape,
      Optional<ConstructorCapability> constructor,
      Optional<IntrinsicId> collectionLiteral,
      boolean defaultCollectionLiteral,
      Optional<IterableCapability> iterable,
      Optional<IndexCapability> index,
      List<MemberDefinition> members,
      List<MemberDefinition> typeMembers,
      boolean hidden) {
    public TypeDefinition {
      Objects.requireNonNull(symbol);
      Objects.requireNonNull(runtimeShape);
      Objects.requireNonNull(constructor);
      Objects.requireNonNull(collectionLiteral);
      Objects.requireNonNull(iterable);
      Objects.requireNonNull(index);
      if (defaultCollectionLiteral && collectionLiteral.isEmpty())
        throw new IllegalArgumentException("default ABI collection requires a materializer");
      members = List.copyOf(members);
      typeMembers = List.copyOf(typeMembers);
    }

    public int arity() {
      return symbol.typeParameters().size();
    }

    public List<String> typeParameters() {
      return symbol.typeParameters().stream().map(TypeParameter::name).toList();
    }
  }

  public List<IntrinsicCandidate> intrinsicCandidates(IntrinsicId intrinsic) {
    Objects.requireNonNull(intrinsic, "intrinsic");
    List<IntrinsicCandidate> result = new ArrayList<>();
    globals.values().stream()
        .flatMap(List::stream)
        .filter(candidate -> candidate.intrinsic() == intrinsic)
        .map(
            candidate ->
                new IntrinsicCandidate(
                    Optional.empty(),
                    candidate.symbol().parameters(),
                    candidate.symbol().type(),
                    candidate.intrinsic().requiresResultRuntimeType()))
        .forEach(result::add);
    for (TypeDefinition type : types.values()) {
      AbiType owner = ownerType(type);
      type.constructor()
          .filter(candidate -> candidate.intrinsic() == intrinsic)
          .map(
              candidate ->
                  new IntrinsicCandidate(Optional.empty(), candidate.parameters(), owner, true))
          .ifPresent(result::add);
      type.members().stream()
          .filter(candidate -> candidate.intrinsic() == intrinsic)
          .map(
              candidate ->
                  new IntrinsicCandidate(
                      Optional.of(owner),
                      candidate.symbol().parameters(),
                      candidate.symbol().type(),
                      candidate.intrinsic().requiresResultRuntimeType()))
          .forEach(result::add);
      type.typeMembers().stream()
          .filter(candidate -> candidate.intrinsic() == intrinsic)
          .map(
              candidate ->
                  new IntrinsicCandidate(
                      Optional.empty(),
                      candidate.symbol().parameters(),
                      candidate.symbol().type(),
                      true))
          .forEach(result::add);
    }
    protocolConformances().stream()
        .flatMap(
            conformance ->
                conformance.witnesses().values().stream()
                    .filter(witness -> witness.intrinsic() == intrinsic)
                    .map(
                        witness ->
                            new IntrinsicCandidate(
                                Optional.of(conformance.concreteType()),
                                witness.parameters(),
                                witness.result(),
                                false)))
        .forEach(result::add);
    return List.copyOf(result);
  }

  public List<IndexCandidate> indexCandidates(IntrinsicId intrinsic) {
    Objects.requireNonNull(intrinsic, "intrinsic");
    return types.values().stream()
        .filter(type -> type.index().isPresent())
        .filter(type -> type.index().orElseThrow().readIntrinsic() == intrinsic)
        .map(
            type -> {
              IndexCapability index = type.index().orElseThrow();
              return new IndexCandidate(
                  ownerType(type),
                  index.keyType(),
                  index.resultType(),
                  index.readIntrinsic(),
                  index.writeIntrinsic());
            })
        .toList();
  }

  public List<WriteCandidate> writeCandidates(IntrinsicId intrinsic) {
    Objects.requireNonNull(intrinsic, "intrinsic");
    List<WriteCandidate> result = new ArrayList<>();
    for (TypeDefinition type : types.values()) {
      AbiType owner = ownerType(type);
      type.members().stream()
          .filter(candidate -> candidate.writeIntrinsic().orElse(null) == intrinsic)
          .map(candidate -> new WriteCandidate(owner, Optional.empty(), candidate.symbol().type()))
          .forEach(result::add);
      type.index()
          .filter(candidate -> candidate.writeIntrinsic().orElse(null) == intrinsic)
          .map(
              candidate ->
                  new WriteCandidate(
                      owner, Optional.of(candidate.keyType()), candidate.resultType()))
          .ifPresent(result::add);
    }
    return List.copyOf(result);
  }

  public List<IterationCandidate> iterationCandidates(IntrinsicId intrinsic) {
    Objects.requireNonNull(intrinsic, "intrinsic");
    return types.values().stream()
        .filter(type -> type.iterable().isPresent())
        .filter(type -> type.iterable().orElseThrow().intrinsic() == intrinsic)
        .map(
            type ->
                new IterationCandidate(
                    ownerType(type), type.iterable().orElseThrow().elementType()))
        .toList();
  }

  public List<ProtocolConformance> protocolConformances() {
    List<ProtocolConformance> result = new ArrayList<>();
    for (TypeDefinition definition : types.values()) {
      AbiType concrete = ownerType(definition);
      definition
          .iterable()
          .ifPresent(
              iterable ->
                  result.add(
                      new ProtocolConformance(
                          definition.symbol().typeParameters().stream()
                              .map(TypeParameter::type)
                              .toList(),
                          concrete,
                          AbiType.declared(
                              "std.core.Iterable",
                              "Iterable",
                              List.of(iterable.elementType()),
                              AbiType.Category.POLYMORPHIC),
                          Map.of(
                              "iterator",
                              new ProtocolWitness(
                                  List.of(),
                                  AbiType.declared(
                                      "std.core.Iterator",
                                      "Iterator",
                                      List.of(iterable.elementType()),
                                      AbiType.Category.POLYMORPHIC),
                                  iterable.intrinsic())))));
      definition.members().stream()
          .filter(member -> member.symbol().name().equals("size"))
          .filter(
              member -> member.symbol().type().equals(BuiltinDeclarations.type("std.core.Integer")))
          .findFirst()
          .ifPresent(
              size ->
                  result.add(
                      new ProtocolConformance(
                          definition.symbol().typeParameters().stream()
                              .map(TypeParameter::type)
                              .toList(),
                          concrete,
                          AbiType.declared(
                              "std.core.Sized", "Sized", List.of(), AbiType.Category.POLYMORPHIC),
                          Map.of(
                              "size",
                              new ProtocolWitness(
                                  List.of(),
                                  BuiltinDeclarations.type("std.core.Integer"),
                                  size.intrinsic())))));
      definition.members().stream()
          .filter(member -> member.symbol().name().equals("toString"))
          .filter(
              member -> member.symbol().type().equals(BuiltinDeclarations.type("std.core.String")))
          .findFirst()
          .ifPresent(
              toString ->
                  result.add(
                      new ProtocolConformance(
                          definition.symbol().typeParameters().stream()
                              .map(TypeParameter::type)
                              .toList(),
                          concrete,
                          AbiType.declared(
                              "std.core.Stringable",
                              "Stringable",
                              List.of(),
                              AbiType.Category.POLYMORPHIC),
                          Map.of(
                              "toString",
                              new ProtocolWitness(
                                  List.of(),
                                  BuiltinDeclarations.type("std.core.String"),
                                  toString.intrinsic())))));
      definition.members().stream()
          .filter(member -> member.symbol().name().equals("compareTo"))
          .filter(
              member -> member.symbol().type().equals(BuiltinDeclarations.type("std.core.Integer")))
          .filter(member -> member.symbol().parameters().size() == 1)
          .filter(member -> member.symbol().parameters().getFirst().type().equals(concrete))
          .findFirst()
          .ifPresent(
              compareTo ->
                  result.add(
                      new ProtocolConformance(
                          definition.symbol().typeParameters().stream()
                              .map(TypeParameter::type)
                              .toList(),
                          concrete,
                          AbiType.declared(
                              "std.core.Comparable",
                              "Comparable",
                              List.of(concrete),
                              AbiType.Category.POLYMORPHIC),
                          Map.of(
                              "compareTo",
                              new ProtocolWitness(
                                  compareTo.symbol().parameters(),
                                  BuiltinDeclarations.type("std.core.Integer"),
                                  compareTo.intrinsic())))));
    }
    TypeDefinition nativeIterator = type("NativeIterator").orElseThrow();
    AbiType iteratorElement = nativeIterator.symbol().typeParameters().getFirst().type();
    result.add(
        new ProtocolConformance(
            List.of(iteratorElement),
            ownerType(nativeIterator),
            AbiType.declared(
                "std.core.Iterator",
                "Iterator",
                List.of(iteratorElement),
                AbiType.Category.POLYMORPHIC),
            Map.of(
                "hasNext",
                new ProtocolWitness(
                    List.of(),
                    BuiltinDeclarations.type("std.core.Boolean"),
                    IntrinsicId.ITERATOR_HAS_NEXT),
                "next",
                new ProtocolWitness(List.of(), iteratorElement, IntrinsicId.ITERATOR_NEXT))));
    return List.copyOf(result);
  }

  public record GlobalDefinition(Symbol symbol, IntrinsicId intrinsic) {}

  public record MemberDefinition(
      Symbol symbol, IntrinsicId intrinsic, Optional<IntrinsicId> writeIntrinsic) {
    public MemberDefinition {
      writeIntrinsic = Objects.requireNonNull(writeIntrinsic);
    }
  }

  public record ConstructorCapability(List<Parameter> parameters, IntrinsicId intrinsic) {
    public ConstructorCapability {
      parameters = List.copyOf(parameters);
    }
  }

  public record IterableCapability(AbiType elementType, IntrinsicId intrinsic) {}

  public record IndexCapability(
      IndexKind kind,
      AbiType keyType,
      AbiType resultType,
      IntrinsicId readIntrinsic,
      Optional<IntrinsicId> writeIntrinsic) {
    public IndexCapability {
      writeIntrinsic = Objects.requireNonNull(writeIntrinsic);
    }
  }

  public record ProtocolConformance(
      List<AbiType> typeParameters,
      AbiType concreteType,
      AbiType interfaceType,
      Map<String, ProtocolWitness> witnesses) {
    public ProtocolConformance {
      typeParameters = List.copyOf(typeParameters);
      Objects.requireNonNull(concreteType, "concreteType");
      Objects.requireNonNull(interfaceType, "interfaceType");
      witnesses = Map.copyOf(witnesses);
    }
  }

  public record ProtocolWitness(List<Parameter> parameters, AbiType result, IntrinsicId intrinsic) {
    public ProtocolWitness {
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(intrinsic, "intrinsic");
    }
  }

  public record IntrinsicCandidate(
      Optional<AbiType> receiver, List<Parameter> parameters, AbiType result, boolean runtimeType) {
    public IntrinsicCandidate {
      receiver = Objects.requireNonNull(receiver, "receiver");
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(result, "result");
    }
  }

  public record IndexCandidate(
      AbiType receiver,
      AbiType index,
      AbiType result,
      IntrinsicId readIntrinsic,
      Optional<IntrinsicId> writeIntrinsic) {
    public IndexCandidate {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(index, "index");
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(readIntrinsic, "readIntrinsic");
      writeIntrinsic = Objects.requireNonNull(writeIntrinsic, "writeIntrinsic");
    }
  }

  public record WriteCandidate(AbiType receiver, Optional<AbiType> index, AbiType value) {
    public WriteCandidate {
      Objects.requireNonNull(receiver, "receiver");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(value, "value");
    }
  }

  public record IterationCandidate(AbiType receiver, AbiType element) {
    public IterationCandidate {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(element, "element");
    }
  }
}
