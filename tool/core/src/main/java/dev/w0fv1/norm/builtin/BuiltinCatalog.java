package dev.w0fv1.norm.builtin;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.abi.RuntimeShape;
import dev.w0fv1.norm.semantic.IndexKind;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeConstraintSolver;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
import dev.w0fv1.norm.semantic.ValueCategory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BuiltinCatalog {
  private static final BuiltinCatalog STANDARD = create();

  private final Map<String, TypeDefinition> types;
  private final Map<String, TypeDefinition> hiddenTypes;
  private final Map<SymbolId, TypeDefinition> typesBySymbol;
  private final Map<String, List<GlobalDefinition>> globals;
  private final Map<SymbolId, Symbol> symbols;
  private final Map<SymbolId, IntrinsicId> intrinsics;
  private final Map<SymbolId, IntrinsicId> writeIntrinsics;
  private final Map<SymbolId, List<SymbolId>> members;

  private BuiltinCatalog(
      Map<String, TypeDefinition> types, Map<String, List<GlobalDefinition>> globals) {
    this.types = Map.copyOf(types);
    this.hiddenTypes =
        Map.of(
            "NativeIterator",
            type("NativeIterator", RuntimeShape.NATIVE_ITERATOR, "T")
                .category(ValueCategory.IDENTITY)
                .build());
    Map<SymbolId, TypeDefinition> indexedTypes = new LinkedHashMap<>();
    types.values().forEach(type -> indexedTypes.put(type.symbol().id(), type));
    typesBySymbol = Map.copyOf(indexedTypes);
    Map<String, List<GlobalDefinition>> copiedGlobals = new LinkedHashMap<>();
    globals.forEach((name, values) -> copiedGlobals.put(name, List.copyOf(values)));
    this.globals = Map.copyOf(copiedGlobals);
    long defaultCollectionLiterals =
        types.values().stream().filter(TypeDefinition::defaultCollectionLiteral).count();
    if (defaultCollectionLiterals != 1) {
      throw new IllegalStateException(
          "builtin catalog requires one default collection literal type");
    }
    Map<SymbolId, Symbol> allSymbols = new LinkedHashMap<>();
    Map<SymbolId, IntrinsicId> allIntrinsics = new LinkedHashMap<>();
    Map<SymbolId, IntrinsicId> allWriteIntrinsics = new LinkedHashMap<>();
    Map<SymbolId, List<SymbolId>> allMembers = new LinkedHashMap<>();
    for (TypeDefinition type : types.values()) {
      putUnique(allSymbols, type.symbol());
      type.constructor()
          .ifPresent(value -> allIntrinsics.put(type.symbol().id(), value.intrinsic()));
      List<SymbolId> memberIds = new ArrayList<>();
      for (MemberDefinition member : type.members()) {
        putUnique(allSymbols, member.symbol());
        allIntrinsics.put(member.symbol().id(), member.intrinsic());
        member
            .writeIntrinsic()
            .ifPresent(value -> allWriteIntrinsics.put(member.symbol().id(), value));
        memberIds.add(member.symbol().id());
      }
      for (MemberDefinition member : type.typeMembers()) {
        putUnique(allSymbols, member.symbol());
        allIntrinsics.put(member.symbol().id(), member.intrinsic());
      }
      allMembers.put(type.symbol().id(), List.copyOf(memberIds));
    }
    for (List<GlobalDefinition> overloads : globals.values()) {
      for (GlobalDefinition global : overloads) {
        putUnique(allSymbols, global.symbol());
        allIntrinsics.put(global.symbol().id(), global.intrinsic());
      }
    }
    symbols = Map.copyOf(allSymbols);
    intrinsics = Map.copyOf(allIntrinsics);
    writeIntrinsics = Map.copyOf(allWriteIntrinsics);
    members = Map.copyOf(allMembers);
    Set<IntrinsicId> expectedIntrinsics = Set.copyOf(EnumSet.allOf(IntrinsicId.class));
    Set<IntrinsicId> declaredIntrinsics = declaredIntrinsics();
    if (!declaredIntrinsics.equals(expectedIntrinsics)) {
      Set<IntrinsicId> missing = EnumSet.copyOf(expectedIntrinsics);
      missing.removeAll(declaredIntrinsics);
      Set<IntrinsicId> unexpected = EnumSet.copyOf(declaredIntrinsics);
      unexpected.removeAll(expectedIntrinsics);
      throw new IllegalStateException(
          "builtin intrinsic catalog mismatch; missing=" + missing + ", unexpected=" + unexpected);
    }
  }

  public static BuiltinCatalog standard() {
    return STANDARD;
  }

  public Map<SymbolId, Symbol> symbols() {
    return symbols;
  }

  public Map<SymbolId, List<SymbolId>> members() {
    return members;
  }

  public Optional<TypeDefinition> type(String name) {
    TypeDefinition definition = types.get(name);
    return Optional.ofNullable(definition != null ? definition : hiddenTypes.get(name));
  }

  public Optional<TypeDefinition> type(SymbolId symbol) {
    return Optional.ofNullable(typesBySymbol.get(symbol));
  }

  public Optional<GlobalDefinition> global(String name) {
    List<GlobalDefinition> overloads = globals.get(name);
    return overloads == null || overloads.isEmpty()
        ? Optional.empty()
        : Optional.of(overloads.getFirst());
  }

  public List<GlobalDefinition> globals(String name) {
    return globals.getOrDefault(name, List.of());
  }

  public Optional<MemberDefinition> member(String owner, String name) {
    TypeDefinition type = types.get(owner);
    if (type == null) return Optional.empty();
    return type.members().stream()
        .filter(member -> member.symbol().name().equals(name))
        .findFirst();
  }

  public List<MemberDefinition> members(String owner, String name) {
    TypeDefinition type = types.get(owner);
    if (type == null) return List.of();
    return type.members().stream().filter(member -> member.symbol().name().equals(name)).toList();
  }

  public Optional<Symbol> member(SemanticType owner, String name) {
    return member(owner.name(), name)
        .map(MemberDefinition::symbol)
        .map(symbol -> substitute(symbol, substitutions(types.get(owner.name()), owner)));
  }

  public List<Symbol> members(SemanticType owner, String name) {
    Map<String, SemanticType> substitutions = substitutions(types.get(owner.name()), owner);
    return members(owner.name(), name).stream()
        .map(MemberDefinition::symbol)
        .map(symbol -> substitute(symbol, substitutions))
        .toList();
  }

  public Optional<Symbol> member(SemanticType owner, SymbolId id) {
    TypeDefinition type = types.get(owner.name());
    if (type == null) return Optional.empty();
    Map<String, SemanticType> substitutions = substitutions(type, owner);
    return type.members().stream()
        .map(MemberDefinition::symbol)
        .filter(symbol -> symbol.id().equals(id))
        .findFirst()
        .map(symbol -> substitute(symbol, substitutions));
  }

  public List<Symbol> typeMembers(String owner, String name) {
    TypeDefinition type = types.get(owner);
    if (type == null) return List.of();
    return type.typeMembers().stream()
        .filter(member -> member.symbol().name().equals(name))
        .map(MemberDefinition::symbol)
        .toList();
  }

  public Optional<IntrinsicId> intrinsic(SymbolId symbol) {
    return Optional.ofNullable(intrinsics.get(symbol));
  }

  public Optional<IntrinsicId> writeIntrinsic(SymbolId symbol) {
    return Optional.ofNullable(writeIntrinsics.get(symbol));
  }

  public Set<IntrinsicId> declaredIntrinsics() {
    Set<IntrinsicId> result = new LinkedHashSet<>(intrinsics.values());
    for (TypeDefinition type : types.values()) {
      type.iterable().map(IterableCapability::intrinsic).ifPresent(result::add);
      type.index()
          .ifPresent(
              index -> {
                result.add(index.readIntrinsic());
                index.writeIntrinsic().ifPresent(result::add);
              });
      type.members().stream()
          .map(MemberDefinition::writeIntrinsic)
          .flatMap(Optional::stream)
          .forEach(result::add);
      type.typeMembers().stream().map(MemberDefinition::intrinsic).forEach(result::add);
    }
    protocolConformances().stream()
        .flatMap(conformance -> conformance.witnesses().values().stream())
        .map(ProtocolWitness::intrinsic)
        .forEach(result::add);
    return Set.copyOf(result);
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
      SemanticType owner = ownerType(type);
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
      SemanticType owner = ownerType(type);
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

  public SemanticType instantiate(String name, List<SemanticType> arguments) {
    TypeDefinition type = Objects.requireNonNull(types.get(name), "unknown builtin type " + name);
    return SemanticType.declared(
        "std.core." + name, name, arguments, type.symbol().type().category());
  }

  public Optional<ResolvedIterable> resolveIterable(SemanticType type) {
    TypeDefinition definition = types.get(type.name());
    if (definition == null || definition.iterable().isEmpty()) return Optional.empty();
    IterableCapability capability = definition.iterable().orElseThrow();
    return Optional.of(
        new ResolvedIterable(
            capability.elementType().substitute(substitutions(definition, type)),
            capability.intrinsic()));
  }

  public List<SemanticType> protocolConformances(SemanticType type) {
    List<SemanticType> result = new ArrayList<>();
    resolveIterable(type)
        .ifPresent(
            iterable ->
                result.add(
                    SemanticType.declared(
                        "std.core.Iterable",
                        "Iterable",
                        List.of(iterable.elementType()),
                        ValueCategory.POLYMORPHIC)));
    member(type, "size")
        .filter(value -> value.type().equals(SemanticType.INTEGER))
        .ifPresent(
            ignored ->
                result.add(
                    SemanticType.declared(
                        "std.core.Sized", "Sized", List.of(), ValueCategory.POLYMORPHIC)));
    member(type, "toString")
        .filter(value -> value.type().equals(SemanticType.STRING))
        .ifPresent(
            ignored ->
                result.add(
                    SemanticType.declared(
                        "std.core.Stringable",
                        "Stringable",
                        List.of(),
                        ValueCategory.POLYMORPHIC)));
    return List.copyOf(result);
  }

  public List<ProtocolConformance> protocolConformances() {
    List<ProtocolConformance> result = new ArrayList<>();
    for (TypeDefinition definition : types.values()) {
      SemanticType concrete = ownerType(definition);
      definition
          .iterable()
          .ifPresent(
              iterable ->
                  result.add(
                      new ProtocolConformance(
                          definition.typeParameters().stream()
                              .map(name -> parameter(definition.symbol().name(), name))
                              .toList(),
                          concrete,
                          SemanticType.declared(
                              "std.core.Iterable",
                              "Iterable",
                              List.of(iterable.elementType()),
                              ValueCategory.POLYMORPHIC),
                          Map.of(
                              "iterator",
                              new ProtocolWitness(
                                  List.of(),
                                  SemanticType.declared(
                                      "std.core.Iterator",
                                      "Iterator",
                                      List.of(iterable.elementType()),
                                      ValueCategory.POLYMORPHIC),
                                  iterable.intrinsic())))));
      definition.members().stream()
          .filter(member -> member.symbol().name().equals("size"))
          .filter(member -> member.symbol().type().equals(SemanticType.INTEGER))
          .findFirst()
          .ifPresent(
              size ->
                  result.add(
                      new ProtocolConformance(
                          definition.typeParameters().stream()
                              .map(name -> parameter(definition.symbol().name(), name))
                              .toList(),
                          concrete,
                          SemanticType.declared(
                              "std.core.Sized", "Sized", List.of(), ValueCategory.POLYMORPHIC),
                          Map.of(
                              "size",
                              new ProtocolWitness(
                                  List.of(), SemanticType.INTEGER, size.intrinsic())))));
      definition.members().stream()
          .filter(member -> member.symbol().name().equals("toString"))
          .filter(member -> member.symbol().type().equals(SemanticType.STRING))
          .findFirst()
          .ifPresent(
              toString ->
                  result.add(
                      new ProtocolConformance(
                          definition.typeParameters().stream()
                              .map(name -> parameter(definition.symbol().name(), name))
                              .toList(),
                          concrete,
                          SemanticType.declared(
                              "std.core.Stringable",
                              "Stringable",
                              List.of(),
                              ValueCategory.POLYMORPHIC),
                          Map.of(
                              "toString",
                              new ProtocolWitness(
                                  List.of(), SemanticType.STRING, toString.intrinsic())))));
    }
    TypeDefinition nativeIterator = hiddenTypes.get("NativeIterator");
    SemanticType iteratorElement = nativeIterator.symbol().typeParameters().getFirst().type();
    result.add(
        new ProtocolConformance(
            List.of(iteratorElement),
            ownerType(nativeIterator),
            SemanticType.declared(
                "std.core.Iterator",
                "Iterator",
                List.of(iteratorElement),
                ValueCategory.POLYMORPHIC),
            Map.of(
                "hasNext",
                new ProtocolWitness(List.of(), SemanticType.BOOLEAN, IntrinsicId.ITERATOR_HAS_NEXT),
                "next",
                new ProtocolWitness(List.of(), iteratorElement, IntrinsicId.ITERATOR_NEXT))));
    return List.copyOf(result);
  }

  public Optional<ResolvedIndex> resolveIndex(SemanticType type) {
    TypeDefinition definition = types.get(type.name());
    if (definition == null || definition.index().isEmpty()) return Optional.empty();
    IndexCapability capability = definition.index().orElseThrow();
    Map<String, SemanticType> substitutions = substitutions(definition, type);
    return Optional.of(
        new ResolvedIndex(
            capability.kind(),
            capability.keyType().substitute(substitutions),
            capability.resultType().substitute(substitutions),
            capability.readIntrinsic(),
            capability.writeIntrinsic()));
  }

  public Optional<List<ParameterInfo>> constructorParameters(SemanticType type) {
    TypeDefinition definition = types.get(type.name());
    if (definition == null || definition.constructor().isEmpty()) return Optional.empty();
    Map<String, SemanticType> substitutions = substitutions(definition, type);
    return Optional.of(
        definition.constructor().orElseThrow().parameters().stream()
            .map(
                parameter ->
                    new ParameterInfo(parameter.name(), parameter.type().substitute(substitutions)))
            .toList());
  }

  public Optional<IntrinsicId> collectionLiteral(SemanticType type) {
    TypeDefinition definition = types.get(type.name());
    return definition == null ? Optional.empty() : definition.collectionLiteral();
  }

  public Optional<ResolvedCollectionLiteral> resolveCollectionLiteral(SemanticType expected) {
    TypeDefinition direct = types.get(expected.name());
    if (direct != null && direct.collectionLiteral().isPresent()) {
      return Optional.of(
          new ResolvedCollectionLiteral(expected, direct.collectionLiteral().orElseThrow()));
    }
    TypeDefinition fallback =
        types.values().stream()
            .filter(TypeDefinition::defaultCollectionLiteral)
            .findFirst()
            .orElseThrow();
    List<SemanticType> variables =
        fallback.symbol().typeParameters().stream().map(TypeParameterInfo::type).toList();
    SemanticType prototype = instantiate(fallback.symbol().name(), variables);
    for (SemanticType conformance : protocolConformances(prototype)) {
      if (!conformance.nonNullable().identity().equals(expected.nonNullable().identity())) continue;
      TypeConstraintSolver solver = new TypeConstraintSolver(variables);
      solver.constrain(conformance, expected);
      TypeConstraintSolver.Solution solution = solver.solve();
      if (!solution.missing().isEmpty() || !solution.conflicts().isEmpty()) continue;
      return Optional.of(
          new ResolvedCollectionLiteral(
              prototype.substitute(solution.substitutions()),
              fallback.collectionLiteral().orElseThrow()));
    }
    return Optional.empty();
  }

  private static BuiltinCatalog create() {
    Map<String, TypeDefinition> types = new LinkedHashMap<>();
    Map<String, List<GlobalDefinition>> globals = new LinkedHashMap<>();
    SemanticType integerType = SemanticType.INTEGER;
    SemanticType codePointType = SemanticType.CODE_POINT;
    SemanticType booleanType = SemanticType.BOOLEAN;
    SemanticType stringType = SemanticType.STRING;
    SemanticType rangeType = declared("Range");
    SemanticType builderType = declared("StringBuilder");
    SemanticType listT = parameter("List", "T");
    SemanticType mapK = parameter("Map", "K");
    SemanticType mapV = parameter("Map", "V");
    SemanticType setT = parameter("Set", "T");
    SemanticType stackT = parameter("Stack", "T");
    SemanticType queueT = parameter("Queue", "T");
    SemanticType dequeT = parameter("Deque", "T");
    SemanticType pairA = parameter("Pair", "A");
    SemanticType pairB = parameter("Pair", "B");
    SemanticType codePointsType =
        SemanticType.declared(
            "std.core.Array", "Array", List.of(codePointType), ValueCategory.VALUE);
    SemanticType graphemesType =
        SemanticType.declared("std.core.Array", "Array", List.of(stringType), ValueCategory.VALUE);
    SemanticType annotationA = methodParameter("Type", "annotation", "A");
    SemanticType invocationR = parameter("FunctionInvocation", "R");
    SemanticType functionContextType =
        SemanticType.declared(
            "std.core.FunctionContext", "FunctionContext", List.of(), ValueCategory.IDENTITY);
    SemanticType systemClockT = globalParameter("__systemClock", "T");
    SemanticType clockNowT = globalParameter("__clockNow", "T");
    SemanticType bytesCreateT = globalParameter("__bytesCreate", "T");
    SemanticType bytesSliceT = globalParameter("__bytesSlice", "T");
    SemanticType bytesJoinT = globalParameter("__bytesJoin", "T");
    SemanticType textEncodeUtf8T = globalParameter("__textEncodeUtf8", "T");
    SemanticType useT = globalParameter("__use", "T");
    SemanticType fileOpenReadT = globalParameter("__fileOpenRead", "T");
    SemanticType fileReadT = globalParameter("__fileRead", "T");
    SemanticType fileOpenWriteT = globalParameter("__fileOpenWrite", "T");
    SemanticType httpSendT = globalParameter("__httpSend", "T");
    SemanticType httpReadT = globalParameter("__httpRead", "T");
    SemanticType reflectT = globalParameter("reflect", "T");
    SemanticType integerArrayType =
        SemanticType.declared("std.core.Array", "Array", List.of(integerType), ValueCategory.VALUE);
    SemanticType stringArrayType =
        SemanticType.declared("std.core.Array", "Array", List.of(stringType), ValueCategory.VALUE);

    addType(types, type(integerType.name(), RuntimeShape.INTEGER));
    addType(types, type(SemanticType.LONG.name(), RuntimeShape.LONG));
    addType(types, type(SemanticType.FLOAT.name(), RuntimeShape.FLOAT));
    addType(types, type(SemanticType.DOUBLE.name(), RuntimeShape.DOUBLE));
    addType(
        types,
        type(SemanticType.NUMBER.name(), RuntimeShape.NUMBER).category(ValueCategory.POLYMORPHIC));
    addType(
        types,
        type(codePointType.name(), RuntimeShape.CODE_POINT)
            .members(
                method(
                    "CodePoint", "scalarValue", integerType, IntrinsicId.CODE_POINT_SCALAR_VALUE),
                method(
                    "CodePoint",
                    "isDecimalDigit",
                    booleanType,
                    IntrinsicId.CODE_POINT_IS_DECIMAL_DIGIT),
                method("CodePoint", "isLetter", booleanType, IntrinsicId.CODE_POINT_IS_LETTER),
                method(
                    "CodePoint", "isWhitespace", booleanType, IntrinsicId.CODE_POINT_IS_WHITESPACE),
                method(
                    "CodePoint", "isUppercase", booleanType, IntrinsicId.CODE_POINT_IS_UPPERCASE),
                method(
                    "CodePoint", "isLowercase", booleanType, IntrinsicId.CODE_POINT_IS_LOWERCASE),
                method(
                    "CodePoint",
                    "isAsciiDigit",
                    booleanType,
                    IntrinsicId.CODE_POINT_IS_ASCII_DIGIT),
                method(
                    "CodePoint",
                    "asciiDigitValue",
                    integerType,
                    IntrinsicId.CODE_POINT_ASCII_DIGIT_VALUE)));
    addType(types, type(booleanType.name(), RuntimeShape.BOOL));
    addType(types, type(SemanticType.VOID.name(), RuntimeShape.VOID));
    addType(
        types,
        type("Type", RuntimeShape.TYPE, "T")
            .members(
                method("Type", "name", stringType, IntrinsicId.TYPE_NAME),
                genericMethod(
                    "Type",
                    "annotation",
                    annotationA.nullable(),
                    IntrinsicId.TYPE_ANNOTATION,
                    List.of(new TypeParameterInfo("A", annotationA)))));
    addType(
        types,
        type("FunctionContext", RuntimeShape.FUNCTION_CONTEXT)
            .category(ValueCategory.IDENTITY)
            .members(
                method("FunctionContext", "name", stringType, IntrinsicId.FUNCTION_CONTEXT_NAME)));
    addType(
        types,
        type("ParameterContext", RuntimeShape.PARAMETER_CONTEXT)
            .category(ValueCategory.IDENTITY)
            .members(
                method(
                    "ParameterContext",
                    "function",
                    functionContextType,
                    IntrinsicId.PARAMETER_CONTEXT_FUNCTION),
                method("ParameterContext", "name", stringType, IntrinsicId.PARAMETER_CONTEXT_NAME),
                method(
                    "ParameterContext",
                    "index",
                    integerType,
                    IntrinsicId.PARAMETER_CONTEXT_INDEX)));
    addType(
        types,
        type("FieldContext", RuntimeShape.FIELD_CONTEXT)
            .category(ValueCategory.IDENTITY)
            .members(
                method("FieldContext", "name", stringType, IntrinsicId.FIELD_CONTEXT_NAME),
                method("FieldContext", "index", integerType, IntrinsicId.FIELD_CONTEXT_INDEX)));
    addType(
        types,
        type("FunctionInvocation", RuntimeShape.FUNCTION_INVOCATION, "R")
            .category(ValueCategory.IDENTITY)
            .members(
                method(
                    "FunctionInvocation",
                    "proceed",
                    invocationR,
                    IntrinsicId.FUNCTION_INVOCATION_PROCEED)));
    addType(
        types,
        type("FunctionCompletion", RuntimeShape.FUNCTION_COMPLETION)
            .category(ValueCategory.IDENTITY)
            .members(
                method(
                    "FunctionCompletion",
                    "succeeded",
                    booleanType,
                    IntrinsicId.FUNCTION_COMPLETION_SUCCEEDED)));
    addType(
        types,
        type(stringType.name(), RuntimeShape.STRING)
            .members(
                method("String", "byteSize", integerType, IntrinsicId.STRING_BYTE_SIZE),
                method("String", "codePointSize", integerType, IntrinsicId.STRING_CODE_POINT_SIZE),
                method("String", "graphemeSize", integerType, IntrinsicId.STRING_GRAPHEME_SIZE),
                method("String", "codePoints", codePointsType, IntrinsicId.STRING_CODE_POINTS),
                method("String", "graphemes", graphemesType, IntrinsicId.STRING_GRAPHEMES),
                method(
                    "String",
                    "sliceCodePoints",
                    stringType,
                    IntrinsicId.STRING_SLICE_CODE_POINTS,
                    parameterInfo("start", integerType),
                    parameterInfo("end", integerType)),
                method(
                    "String",
                    "split",
                    graphemesType,
                    IntrinsicId.STRING_SPLIT,
                    parameterInfo("separator", stringType)),
                method("String", "isEmpty", booleanType, IntrinsicId.STRING_IS_EMPTY),
                method(
                    "String",
                    "contains",
                    booleanType,
                    IntrinsicId.STRING_CONTAINS,
                    parameterInfo("value", stringType)),
                method(
                    "String",
                    "startsWith",
                    booleanType,
                    IntrinsicId.STRING_STARTS_WITH,
                    parameterInfo("prefix", stringType)),
                method(
                    "String",
                    "endsWith",
                    booleanType,
                    IntrinsicId.STRING_ENDS_WITH,
                    parameterInfo("suffix", stringType)),
                method(
                    "String",
                    "sliceGraphemes",
                    stringType,
                    IntrinsicId.STRING_SLICE_GRAPHEMES,
                    parameterInfo("start", integerType),
                    parameterInfo("end", integerType)),
                method(
                    "String",
                    "replace",
                    stringType,
                    IntrinsicId.STRING_REPLACE,
                    parameterInfo("target", stringType),
                    parameterInfo("replacement", stringType)),
                method(
                    "String",
                    "replaceFirst",
                    stringType,
                    IntrinsicId.STRING_REPLACE_FIRST,
                    parameterInfo("target", stringType),
                    parameterInfo("replacement", stringType)),
                method("String", "trim", stringType, IntrinsicId.STRING_TRIM),
                method("String", "trimStart", stringType, IntrinsicId.STRING_TRIM_START),
                method("String", "trimEnd", stringType, IntrinsicId.STRING_TRIM_END),
                method("String", "toLowercase", stringType, IntrinsicId.STRING_TO_LOWERCASE),
                method("String", "toUppercase", stringType, IntrinsicId.STRING_TO_UPPERCASE),
                method(
                    "String",
                    "equalsIgnoreCaseAscii",
                    booleanType,
                    IntrinsicId.STRING_EQUALS_IGNORE_CASE_ASCII,
                    parameterInfo("other", stringType)),
                method(
                    "String",
                    "compareCodePoints",
                    integerType,
                    IntrinsicId.STRING_COMPARE_CODE_POINTS,
                    parameterInfo("right", stringType)),
                method("String", "normalizeNfc", stringType, IntrinsicId.STRING_NORMALIZE_NFC),
                method("String", "normalizeNfd", stringType, IntrinsicId.STRING_NORMALIZE_NFD),
                method("String", "normalizeNfkc", stringType, IntrinsicId.STRING_NORMALIZE_NFKC),
                method("String", "normalizeNfkd", stringType, IntrinsicId.STRING_NORMALIZE_NFKD),
                method(
                    "String", "isNormalizedNfc", booleanType, IntrinsicId.STRING_IS_NORMALIZED_NFC),
                method(
                    "String", "isNormalizedNfd", booleanType, IntrinsicId.STRING_IS_NORMALIZED_NFD),
                method(
                    "String",
                    "isNormalizedNfkc",
                    booleanType,
                    IntrinsicId.STRING_IS_NORMALIZED_NFKC),
                method(
                    "String",
                    "isNormalizedNfkd",
                    booleanType,
                    IntrinsicId.STRING_IS_NORMALIZED_NFKD)));
    addType(
        types,
        type("Array", RuntimeShape.ARRAY, "T")
            .constructor(IntrinsicId.ARRAY_CONSTRUCT)
            .collectionLiteral(IntrinsicId.ARRAY_CONSTRUCT)
            .defaultCollectionLiteral()
            .iterable(parameter("Array", "T"), IntrinsicId.ARRAY_ITERATOR)
            .index(
                IndexKind.INTEGER,
                integerType,
                parameter("Array", "T"),
                IntrinsicId.ARRAY_INDEX_READ,
                IntrinsicId.ARRAY_INDEX_WRITE)
            .members(
                method("Array", "size", integerType, IntrinsicId.SIZE),
                method("Array", "last", parameter("Array", "T"), IntrinsicId.ARRAY_LAST),
                method(
                    "Array",
                    "reversed",
                    SemanticType.declared(
                        "std.core.Array",
                        "Array",
                        List.of(parameter("Array", "T")),
                        ValueCategory.VALUE),
                    IntrinsicId.ARRAY_REVERSED))
            .typeMembers(
                typeMethod(
                    "Array",
                    "filled",
                    SemanticType.declared(
                        "std.core.Array",
                        "Array",
                        List.of(parameter("Array", "T")),
                        ValueCategory.VALUE),
                    IntrinsicId.ARRAY_FILLED,
                    List.of("T"),
                    parameterInfo("size", integerType),
                    parameterInfo("value", parameter("Array", "T")))));
    addType(
        types,
        type("List", RuntimeShape.LIST, "T")
            .constructor(IntrinsicId.LIST_CONSTRUCT)
            .collectionLiteral(IntrinsicId.LIST_CONSTRUCT)
            .iterable(listT, IntrinsicId.LIST_ITERATOR)
            .index(
                IndexKind.INTEGER,
                integerType,
                listT,
                IntrinsicId.LIST_INDEX_READ,
                IntrinsicId.LIST_INDEX_WRITE)
            .members(
                method(
                    "List",
                    "add",
                    SemanticType.VOID,
                    IntrinsicId.LIST_ADD,
                    parameterInfo("value", listT)),
                method(
                    "List",
                    "get",
                    listT,
                    IntrinsicId.LIST_GET,
                    parameterInfo("index", integerType)),
                method(
                    "List",
                    "removeAt",
                    listT,
                    IntrinsicId.LIST_REMOVE_AT,
                    parameterInfo("index", integerType)),
                method("List", "last", listT, IntrinsicId.LIST_LAST),
                method("List", "removeLast", listT, IntrinsicId.LIST_REMOVE_LAST),
                method(
                    "List",
                    "reversed",
                    SemanticType.declared(
                        "std.core.List", "List", List.of(listT), ValueCategory.VALUE),
                    IntrinsicId.LIST_REVERSED),
                method("List", "size", integerType, IntrinsicId.SIZE),
                method("List", "isEmpty", booleanType, IntrinsicId.IS_EMPTY))
            .typeMembers(
                typeMethod(
                    "List",
                    "filled",
                    SemanticType.declared(
                        "std.core.List", "List", List.of(listT), ValueCategory.VALUE),
                    IntrinsicId.LIST_FILLED,
                    List.of("T"),
                    parameterInfo("size", integerType),
                    parameterInfo("value", listT))));
    addType(
        types,
        type("Map", RuntimeShape.MAP, "K", "V")
            .constructor(IntrinsicId.MAP_CONSTRUCT)
            .iterable(
                SemanticType.declared(
                    "std.core.Pair", "Pair", List.of(mapK, mapV), ValueCategory.VALUE),
                IntrinsicId.MAP_ITERATOR)
            .index(
                IndexKind.VALUE,
                mapK,
                mapV,
                IntrinsicId.MAP_INDEX_READ,
                IntrinsicId.MAP_INDEX_WRITE)
            .members(
                method(
                    "Map",
                    "put",
                    SemanticType.VOID,
                    IntrinsicId.MAP_PUT,
                    parameterInfo("key", mapK),
                    parameterInfo("value", mapV)),
                method(
                    "Map", "get", mapV.nullable(), IntrinsicId.MAP_GET, parameterInfo("key", mapK)),
                method(
                    "Map",
                    "containsKey",
                    booleanType,
                    IntrinsicId.MAP_CONTAINS_KEY,
                    parameterInfo("key", mapK)),
                method(
                    "Map",
                    "remove",
                    booleanType,
                    IntrinsicId.MAP_REMOVE,
                    parameterInfo("key", mapK)),
                method("Map", "size", integerType, IntrinsicId.SIZE),
                method("Map", "isEmpty", booleanType, IntrinsicId.IS_EMPTY)));
    addType(
        types,
        type("Set", RuntimeShape.SET, "T")
            .constructor(IntrinsicId.SET_CONSTRUCT)
            .iterable(setT, IntrinsicId.SET_ITERATOR)
            .members(
                method(
                    "Set", "add", booleanType, IntrinsicId.SET_ADD, parameterInfo("value", setT)),
                method(
                    "Set",
                    "contains",
                    booleanType,
                    IntrinsicId.SET_CONTAINS,
                    parameterInfo("value", setT)),
                method(
                    "Set",
                    "remove",
                    booleanType,
                    IntrinsicId.SET_REMOVE,
                    parameterInfo("value", setT)),
                method("Set", "size", integerType, IntrinsicId.SIZE),
                method("Set", "isEmpty", booleanType, IntrinsicId.IS_EMPTY)));
    addType(
        types,
        type("Stack", RuntimeShape.STACK, "T")
            .constructor(IntrinsicId.STACK_CONSTRUCT)
            .iterable(stackT, IntrinsicId.STACK_ITERATOR)
            .members(
                method(
                    "Stack",
                    "push",
                    SemanticType.VOID,
                    IntrinsicId.STACK_PUSH,
                    parameterInfo("value", stackT)),
                method("Stack", "pop", stackT, IntrinsicId.STACK_POP),
                method("Stack", "peek", stackT, IntrinsicId.STACK_PEEK),
                method("Stack", "size", integerType, IntrinsicId.SIZE),
                method("Stack", "isEmpty", booleanType, IntrinsicId.IS_EMPTY)));
    addType(
        types,
        type("Queue", RuntimeShape.QUEUE, "T")
            .constructor(IntrinsicId.QUEUE_CONSTRUCT)
            .iterable(queueT, IntrinsicId.QUEUE_ITERATOR)
            .members(
                method(
                    "Queue",
                    "add",
                    SemanticType.VOID,
                    IntrinsicId.QUEUE_ADD,
                    parameterInfo("value", queueT)),
                method("Queue", "remove", queueT, IntrinsicId.QUEUE_REMOVE),
                method("Queue", "peek", queueT, IntrinsicId.QUEUE_PEEK),
                method("Queue", "size", integerType, IntrinsicId.SIZE),
                method("Queue", "isEmpty", booleanType, IntrinsicId.IS_EMPTY)));
    addType(
        types,
        type("Deque", RuntimeShape.DEQUE, "T")
            .constructor(IntrinsicId.DEQUE_CONSTRUCT)
            .iterable(dequeT, IntrinsicId.DEQUE_ITERATOR)
            .members(
                method(
                    "Deque",
                    "addFirst",
                    SemanticType.VOID,
                    IntrinsicId.DEQUE_ADD_FIRST,
                    parameterInfo("value", dequeT)),
                method(
                    "Deque",
                    "addLast",
                    SemanticType.VOID,
                    IntrinsicId.DEQUE_ADD_LAST,
                    parameterInfo("value", dequeT)),
                method("Deque", "removeFirst", dequeT, IntrinsicId.DEQUE_REMOVE_FIRST),
                method("Deque", "removeLast", dequeT, IntrinsicId.DEQUE_REMOVE_LAST),
                method("Deque", "peekFirst", dequeT, IntrinsicId.DEQUE_PEEK_FIRST),
                method("Deque", "peekLast", dequeT, IntrinsicId.DEQUE_PEEK_LAST),
                method("Deque", "size", integerType, IntrinsicId.SIZE),
                method("Deque", "isEmpty", booleanType, IntrinsicId.IS_EMPTY)));
    addType(
        types,
        type("Pair", RuntimeShape.PAIR, "A", "B")
            .constructor(
                IntrinsicId.PAIR_CONSTRUCT,
                parameterInfo("first", pairA),
                parameterInfo("second", pairB))
            .members(
                field(
                    "Pair",
                    "first",
                    pairA,
                    IntrinsicId.PAIR_FIRST_READ,
                    IntrinsicId.PAIR_FIRST_WRITE),
                field(
                    "Pair",
                    "second",
                    pairB,
                    IntrinsicId.PAIR_SECOND_READ,
                    IntrinsicId.PAIR_SECOND_WRITE)));
    addType(
        types,
        type("Range", RuntimeShape.RANGE)
            .constructor(
                IntrinsicId.RANGE_CONSTRUCT,
                parameterInfo("start", integerType),
                parameterInfo("end", integerType))
            .iterable(integerType, IntrinsicId.RANGE_ITERATOR)
            .members(method("Range", "size", integerType, IntrinsicId.SIZE)));
    addType(
        types,
        type("StringBuilder", RuntimeShape.STRING_BUILDER)
            .constructor(IntrinsicId.STRING_BUILDER_CONSTRUCT)
            .members(
                method(
                    "StringBuilder",
                    "append",
                    builderType,
                    IntrinsicId.BUILDER_APPEND,
                    parameterInfo("value", SemanticType.DYNAMIC)),
                method("StringBuilder", "toString", stringType, IntrinsicId.BUILDER_TO_STRING),
                method("StringBuilder", "size", integerType, IntrinsicId.SIZE)));

    addGlobal(
        globals,
        genericGlobal(
            "reflect",
            SemanticType.declared("std.core.Type", "Type", List.of(reflectT), ValueCategory.VALUE),
            IntrinsicId.REFLECT_TYPE,
            List.of(new TypeParameterInfo("T", reflectT))));
    addGlobal(
        globals,
        global(
            "printLine",
            SemanticType.VOID,
            IntrinsicId.PRINT_LINE,
            parameterInfo("value", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        global(
            "expectedOutputLine",
            SemanticType.VOID,
            IntrinsicId.EXPECTED_OUTPUT_LINE,
            parameterInfo("value", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        global(
            "require",
            SemanticType.VOID,
            IntrinsicId.REQUIRE_ARGUMENT,
            parameterInfo("condition", booleanType),
            parameterInfo("message", stringType)));
    addGlobal(
        globals,
        global(
            "__publishModule",
            SemanticType.VOID,
            IntrinsicId.PUBLISH_MODULE,
            parameterInfo("name", stringType),
            parameterInfo("version", integerType),
            parameterInfo(
                "exports",
                SemanticType.declared(
                    "std.core.List", "List", List.of(stringType), ValueCategory.VALUE)),
            parameterInfo(
                "dependencyNames",
                SemanticType.declared(
                    "std.core.List", "List", List.of(stringType), ValueCategory.VALUE)),
            parameterInfo(
                "dependencyVersions",
                SemanticType.declared(
                    "std.core.List", "List", List.of(integerType), ValueCategory.VALUE))));
    addGlobal(
        globals,
        genericGlobal(
            "__bytesCreate",
            bytesCreateT,
            IntrinsicId.IO_BYTES_CREATE,
            List.of(new TypeParameterInfo("T", bytesCreateT)),
            parameterInfo("values", integerArrayType)));
    addGlobal(
        globals,
        global(
            "__bytesSize",
            integerType,
            IntrinsicId.IO_BYTES_SIZE,
            parameterInfo("value", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        global(
            "__bytesAt",
            integerType,
            IntrinsicId.IO_BYTES_AT,
            parameterInfo("value", SemanticType.DYNAMIC),
            parameterInfo("index", integerType)));
    addGlobal(
        globals,
        genericGlobal(
            "__bytesSlice",
            bytesSliceT,
            IntrinsicId.IO_BYTES_SLICE,
            List.of(new TypeParameterInfo("T", bytesSliceT)),
            parameterInfo("value", SemanticType.DYNAMIC),
            parameterInfo("start", integerType),
            parameterInfo("length", integerType)));
    addGlobal(
        globals,
        global(
            "__bytesToArray",
            integerArrayType,
            IntrinsicId.IO_BYTES_TO_ARRAY,
            parameterInfo("value", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        genericGlobal(
            "__bytesJoin",
            bytesJoinT,
            IntrinsicId.IO_BYTES_JOIN,
            List.of(new TypeParameterInfo("T", bytesJoinT)),
            parameterInfo("values", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        genericGlobal(
            "__textEncodeUtf8",
            textEncodeUtf8T.nullable(),
            IntrinsicId.IO_TEXT_ENCODE_UTF8,
            List.of(new TypeParameterInfo("T", textEncodeUtf8T)),
            parameterInfo("text", stringType)));
    addGlobal(
        globals,
        global(
            "__textDecodeUtf8",
            stringType.nullable(),
            IntrinsicId.IO_TEXT_DECODE_UTF8,
            parameterInfo("content", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        genericGlobal(
            "__use",
            useT,
            IntrinsicId.IO_USE,
            List.of(new TypeParameterInfo("T", useT)),
            parameterInfo("close", SemanticType.function(SemanticType.VOID, List.of())),
            parameterInfo("body", SemanticType.function(useT, List.of()))));
    addGlobal(
        globals,
        genericGlobal(
            "__fileOpenRead",
            fileOpenReadT,
            IntrinsicId.FILE_OPEN_READ,
            List.of(new TypeParameterInfo("T", fileOpenReadT)),
            parameterInfo("path", stringType)));
    addGlobal(
        globals,
        genericGlobal(
            "__fileRead",
            fileReadT.nullable(),
            IntrinsicId.FILE_READER_READ,
            List.of(new TypeParameterInfo("T", fileReadT)),
            parameterInfo("reader", SemanticType.DYNAMIC),
            parameterInfo("maximumBytes", integerType)));
    addGlobal(
        globals,
        genericGlobal(
            "__fileOpenWrite",
            fileOpenWriteT,
            IntrinsicId.FILE_OPEN_WRITE,
            List.of(new TypeParameterInfo("T", fileOpenWriteT)),
            parameterInfo("path", stringType),
            parameterInfo("mode", stringType)));
    addGlobal(
        globals,
        global(
            "__fileWrite",
            integerType,
            IntrinsicId.FILE_WRITER_WRITE,
            parameterInfo("writer", SemanticType.DYNAMIC),
            parameterInfo("content", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        global(
            "__fileFlush",
            SemanticType.VOID,
            IntrinsicId.FILE_WRITER_FLUSH,
            parameterInfo("writer", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        global(
            "__fileSync",
            SemanticType.VOID,
            IntrinsicId.FILE_WRITER_SYNC,
            parameterInfo("writer", SemanticType.DYNAMIC),
            parameterInfo("mode", stringType)));
    addGlobal(
        globals,
        global(
            "__fileClose",
            SemanticType.VOID,
            IntrinsicId.FILE_CLOSE,
            parameterInfo("resource", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        genericGlobal(
            "__httpSend",
            httpSendT,
            IntrinsicId.HTTP_SEND,
            List.of(new TypeParameterInfo("T", httpSendT)),
            parameterInfo("method", stringType),
            parameterInfo("uri", stringType),
            parameterInfo("headers", stringArrayType),
            parameterInfo("body", SemanticType.DYNAMIC),
            parameterInfo("timeoutSeconds", SemanticType.LONG),
            parameterInfo("timeoutNanoseconds", integerType)));
    addGlobal(
        globals,
        global(
            "__httpStatus",
            integerType,
            IntrinsicId.HTTP_RESPONSE_STATUS,
            parameterInfo("response", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        global(
            "__httpHeaders",
            stringArrayType,
            IntrinsicId.HTTP_RESPONSE_HEADERS,
            parameterInfo("response", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        genericGlobal(
            "__httpRead",
            httpReadT.nullable(),
            IntrinsicId.HTTP_RESPONSE_READ,
            List.of(new TypeParameterInfo("T", httpReadT)),
            parameterInfo("response", SemanticType.DYNAMIC),
            parameterInfo("maximumBytes", integerType)));
    addGlobal(
        globals,
        global(
            "__httpClose",
            SemanticType.VOID,
            IntrinsicId.HTTP_RESPONSE_CLOSE,
            parameterInfo("response", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        genericGlobal(
            "__systemClock",
            systemClockT,
            IntrinsicId.TIME_SYSTEM_CLOCK,
            List.of(new TypeParameterInfo("T", systemClockT))));
    addGlobal(
        globals,
        genericGlobal(
            "__clockNow",
            clockNowT,
            IntrinsicId.TIME_CLOCK_NOW,
            List.of(new TypeParameterInfo("T", clockNowT)),
            parameterInfo("clock", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        global(
            "range",
            rangeType,
            IntrinsicId.RANGE_CONSTRUCT,
            parameterInfo("start", integerType),
            parameterInfo("end", integerType)));
    addGlobal(
        globals,
        global(
            "range",
            rangeType,
            IntrinsicId.RANGE_CONSTRUCT,
            parameterInfo("start", integerType),
            parameterInfo("end", integerType),
            parameterInfo("step", integerType)));
    return new BuiltinCatalog(types, globals);
  }

  private static TypeBuilder type(String name, RuntimeShape shape, String... parameters) {
    return new TypeBuilder(name, shape, List.of(parameters));
  }

  private static MemberDefinition method(
      String owner,
      String name,
      SemanticType result,
      IntrinsicId intrinsic,
      ParameterInfo... parameters) {
    return new MemberDefinition(
        member(owner, name, SymbolKind.METHOD, result, parameters), intrinsic, Optional.empty());
  }

  private static MemberDefinition typeMethod(
      String owner,
      String name,
      SemanticType result,
      IntrinsicId intrinsic,
      List<String> typeParameters,
      ParameterInfo... parameters) {
    Symbol base = member(owner, name, SymbolKind.TYPE_METHOD, result, parameters);
    Symbol symbol =
        new Symbol(
            base.id(),
            base.name(),
            base.kind(),
            base.type(),
            base.declaration(),
            base.owner(),
            typeParameters.stream()
                .map(value -> new TypeParameterInfo(value, parameter(owner, value)))
                .toList(),
            base.parameters(),
            base.documentation());
    return new MemberDefinition(symbol, intrinsic, Optional.empty());
  }

  private static MemberDefinition genericMethod(
      String owner,
      String name,
      SemanticType result,
      IntrinsicId intrinsic,
      List<TypeParameterInfo> typeParameters,
      ParameterInfo... parameters) {
    Symbol base = member(owner, name, SymbolKind.METHOD, result, parameters);
    Symbol symbol =
        new Symbol(
            base.id(),
            base.name(),
            base.kind(),
            base.type(),
            base.declaration(),
            base.owner(),
            typeParameters,
            base.parameters(),
            base.documentation());
    return new MemberDefinition(symbol, intrinsic, Optional.empty());
  }

  private static MemberDefinition field(
      String owner, String name, SemanticType result, IntrinsicId read, IntrinsicId write) {
    return new MemberDefinition(
        member(owner, name, SymbolKind.FIELD, result), read, Optional.of(write));
  }

  private static Symbol member(
      String owner,
      String name,
      SymbolKind kind,
      SemanticType result,
      ParameterInfo... parameters) {
    return new Symbol(
        SymbolId.builtin("member/" + owner + "/" + name + "/" + signature(parameters)),
        name,
        kind,
        result,
        Optional.empty(),
        Optional.of(SymbolId.builtin("type/" + owner)),
        List.of(),
        List.of(parameters),
        "Norm " + owner + " " + kind.name().toLowerCase());
  }

  private static GlobalDefinition global(
      String name, SemanticType result, IntrinsicId intrinsic, ParameterInfo... parameters) {
    Symbol symbol =
        new Symbol(
            SymbolId.builtin("function/" + name + "/" + signature(parameters)),
            name,
            SymbolKind.FUNCTION,
            result,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            List.of(parameters),
            documentation(name));
    return new GlobalDefinition(symbol, intrinsic);
  }

  private static GlobalDefinition genericGlobal(
      String name,
      SemanticType result,
      IntrinsicId intrinsic,
      List<TypeParameterInfo> typeParameters,
      ParameterInfo... parameters) {
    GlobalDefinition base = global(name, result, intrinsic, parameters);
    Symbol value = base.symbol();
    return new GlobalDefinition(
        new Symbol(
            value.id(),
            value.name(),
            value.kind(),
            value.type(),
            value.declaration(),
            value.owner(),
            typeParameters,
            value.parameters(),
            value.documentation()),
        intrinsic);
  }

  private static void addType(Map<String, TypeDefinition> values, TypeBuilder builder) {
    if (Set.of(
            RuntimeShape.INTEGER,
            RuntimeShape.LONG,
            RuntimeShape.FLOAT,
            RuntimeShape.DOUBLE,
            RuntimeShape.NUMBER,
            RuntimeShape.CODE_POINT,
            RuntimeShape.BOOL,
            RuntimeShape.STRING,
            RuntimeShape.RANGE)
        .contains(builder.shape)) {
      builder.stringable();
    }
    TypeDefinition definition = builder.build();
    if (values.putIfAbsent(definition.symbol().name(), definition) != null) {
      throw new IllegalStateException("duplicate builtin type " + definition.symbol().name());
    }
  }

  private static void addGlobal(
      Map<String, List<GlobalDefinition>> values, GlobalDefinition value) {
    List<GlobalDefinition> overloads =
        values.computeIfAbsent(value.symbol().name(), ignored -> new ArrayList<>());
    if (overloads.stream()
        .anyMatch(candidate -> candidate.symbol().id().equals(value.symbol().id()))) {
      throw new IllegalStateException("duplicate builtin global " + value.symbol().name());
    }
    overloads.add(value);
  }

  private static String signature(ParameterInfo... parameters) {
    return java.util.Arrays.stream(parameters)
        .map(parameter -> parameter.type().displayName())
        .collect(java.util.stream.Collectors.joining(",", "(", ")"));
  }

  private static void putUnique(Map<SymbolId, Symbol> values, Symbol value) {
    if (values.putIfAbsent(value.id(), value) != null) {
      throw new IllegalStateException("duplicate builtin symbol " + value.id().value());
    }
  }

  private static SemanticType declared(String name) {
    return new SemanticType(name);
  }

  private static SemanticType parameter(String owner, String name) {
    return SemanticType.parameter("std.core." + owner + "/" + name, name);
  }

  private static SemanticType methodParameter(String owner, String method, String name) {
    return SemanticType.parameter("std.core." + owner + "/" + method + "/" + name, name);
  }

  private static SemanticType globalParameter(String function, String name) {
    return SemanticType.parameter("std.core." + function + "/" + name, name);
  }

  private static SemanticType ownerType(TypeDefinition definition) {
    List<SemanticType> arguments =
        definition.typeParameters().stream()
            .map(name -> parameter(definition.symbol().name(), name))
            .toList();
    return SemanticType.declared(
        definition.symbol().type().identity(),
        definition.symbol().name(),
        arguments,
        definition.symbol().type().category());
  }

  private static ParameterInfo parameterInfo(String name, SemanticType type) {
    return new ParameterInfo(name, type);
  }

  private static Map<String, SemanticType> substitutions(
      TypeDefinition definition, SemanticType instance) {
    if (definition == null) return Map.of();
    Map<String, SemanticType> result = new LinkedHashMap<>();
    for (int index = 0;
        index < Math.min(definition.typeParameters().size(), instance.arguments().size());
        index++) {
      result.put(
          "std.core." + definition.symbol().name() + "/" + definition.typeParameters().get(index),
          instance.arguments().get(index));
    }
    return Map.copyOf(result);
  }

  private static Symbol substitute(Symbol symbol, Map<String, SemanticType> substitutions) {
    return new Symbol(
        symbol.id(),
        symbol.name(),
        symbol.kind(),
        symbol.type().substitute(substitutions),
        symbol.declaration(),
        symbol.owner(),
        symbol.typeParameters(),
        symbol.parameters().stream()
            .map(
                parameter ->
                    new ParameterInfo(parameter.name(), parameter.type().substitute(substitutions)))
            .toList(),
        symbol.documentation());
  }

  private static String documentation(String name) {
    return switch (name) {
      case "Array" -> "Fixed-length indexed values.";
      case "List" -> "Dynamic-length sequence.";
      case "Map" -> "Key-value container.";
      case "Set" -> "Unique-value container.";
      case "Stack" -> "LIFO container with push, pop, and peek.";
      case "Queue" -> "FIFO container with add, remove, and peek.";
      case "Deque" -> "Double-ended queue.";
      case "Pair" -> "Pair with first and second values.";
      case "Range" -> "end-exclusive integer range.";
      case "StringBuilder" -> "Mutable builder for efficient string concatenation.";
      case "range" -> "Creates an end-exclusive integer range.";
      case "printLine" -> "Writes one value followed by a newline.";
      case "expectedOutputLine" -> "Declares one expected output line for a test program.";
      default -> "Norm built-in.";
    };
  }

  public record TypeDefinition(
      Symbol symbol,
      List<String> typeParameters,
      RuntimeShape runtimeShape,
      Optional<ConstructorCapability> constructor,
      Optional<IntrinsicId> collectionLiteral,
      boolean defaultCollectionLiteral,
      Optional<IterableCapability> iterable,
      Optional<IndexCapability> index,
      List<MemberDefinition> members,
      List<MemberDefinition> typeMembers) {
    public TypeDefinition {
      typeParameters = List.copyOf(typeParameters);
      constructor = Objects.requireNonNull(constructor);
      collectionLiteral = Objects.requireNonNull(collectionLiteral);
      if (defaultCollectionLiteral && collectionLiteral.isEmpty()) {
        throw new IllegalArgumentException(
            "default collection literal type requires a materializer");
      }
      iterable = Objects.requireNonNull(iterable);
      index = Objects.requireNonNull(index);
      members = List.copyOf(members);
      typeMembers = List.copyOf(typeMembers);
    }

    public int arity() {
      return typeParameters.size();
    }
  }

  public record GlobalDefinition(Symbol symbol, IntrinsicId intrinsic) {}

  public record MemberDefinition(
      Symbol symbol, IntrinsicId intrinsic, Optional<IntrinsicId> writeIntrinsic) {
    public MemberDefinition {
      writeIntrinsic = Objects.requireNonNull(writeIntrinsic);
    }
  }

  public record ConstructorCapability(List<ParameterInfo> parameters, IntrinsicId intrinsic) {
    public ConstructorCapability {
      parameters = List.copyOf(parameters);
    }
  }

  public record IterableCapability(SemanticType elementType, IntrinsicId intrinsic) {}

  public record IndexCapability(
      IndexKind kind,
      SemanticType keyType,
      SemanticType resultType,
      IntrinsicId readIntrinsic,
      Optional<IntrinsicId> writeIntrinsic) {
    public IndexCapability {
      writeIntrinsic = Objects.requireNonNull(writeIntrinsic);
    }
  }

  public record ResolvedIterable(SemanticType elementType, IntrinsicId intrinsic) {}

  public record ResolvedCollectionLiteral(SemanticType type, IntrinsicId intrinsic) {}

  public record ProtocolConformance(
      List<SemanticType> typeParameters,
      SemanticType concreteType,
      SemanticType interfaceType,
      Map<String, ProtocolWitness> witnesses) {
    public ProtocolConformance {
      typeParameters = List.copyOf(typeParameters);
      Objects.requireNonNull(concreteType, "concreteType");
      Objects.requireNonNull(interfaceType, "interfaceType");
      witnesses = Map.copyOf(witnesses);
    }
  }

  public record ProtocolWitness(
      List<ParameterInfo> parameters, SemanticType result, IntrinsicId intrinsic) {
    public ProtocolWitness {
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(result, "result");
      Objects.requireNonNull(intrinsic, "intrinsic");
    }
  }

  public record ResolvedIndex(
      IndexKind kind,
      SemanticType keyType,
      SemanticType resultType,
      IntrinsicId readIntrinsic,
      Optional<IntrinsicId> writeIntrinsic) {}

  public record IntrinsicCandidate(
      Optional<SemanticType> receiver,
      List<ParameterInfo> parameters,
      SemanticType result,
      boolean runtimeType) {
    public IntrinsicCandidate {
      receiver = Objects.requireNonNull(receiver, "receiver");
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(result, "result");
    }
  }

  public record IndexCandidate(
      SemanticType receiver,
      SemanticType index,
      SemanticType result,
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

  public record WriteCandidate(
      SemanticType receiver, Optional<SemanticType> index, SemanticType value) {
    public WriteCandidate {
      Objects.requireNonNull(receiver, "receiver");
      index = Objects.requireNonNull(index, "index");
      Objects.requireNonNull(value, "value");
    }
  }

  public record IterationCandidate(SemanticType receiver, SemanticType element) {
    public IterationCandidate {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(element, "element");
    }
  }

  private static final class TypeBuilder {
    private final String name;
    private final RuntimeShape shape;
    private final List<String> parameters;
    private ConstructorCapability constructor;
    private IntrinsicId collectionLiteral;
    private boolean defaultCollectionLiteral;
    private IterableCapability iterable;
    private IndexCapability index;
    private List<MemberDefinition> members = List.of();
    private List<MemberDefinition> typeMembers = List.of();
    private ValueCategory category;

    private TypeBuilder(String name, RuntimeShape shape, List<String> parameters) {
      this.name = name;
      this.shape = shape;
      this.parameters = parameters;
    }

    private TypeBuilder constructor(IntrinsicId intrinsic, ParameterInfo... parameters) {
      constructor = new ConstructorCapability(List.of(parameters), intrinsic);
      return this;
    }

    private TypeBuilder collectionLiteral(IntrinsicId intrinsic) {
      collectionLiteral = Objects.requireNonNull(intrinsic, "intrinsic");
      return this;
    }

    private TypeBuilder defaultCollectionLiteral() {
      defaultCollectionLiteral = true;
      return this;
    }

    private TypeBuilder category(ValueCategory value) {
      category = Objects.requireNonNull(value, "value");
      return this;
    }

    private TypeBuilder iterable(SemanticType element, IntrinsicId intrinsic) {
      iterable = new IterableCapability(element, intrinsic);
      return this;
    }

    private TypeBuilder index(
        IndexKind kind,
        SemanticType key,
        SemanticType result,
        IntrinsicId read,
        IntrinsicId write) {
      index = new IndexCapability(kind, key, result, read, Optional.of(write));
      return this;
    }

    private TypeBuilder members(MemberDefinition... values) {
      members = List.of(values);
      return this;
    }

    private TypeBuilder stringable() {
      if (members.stream().noneMatch(member -> member.symbol().name().equals("toString"))) {
        List<MemberDefinition> updated = new ArrayList<>(members);
        updated.add(method(name, "toString", SemanticType.STRING, IntrinsicId.TO_STRING));
        members = List.copyOf(updated);
      }
      return this;
    }

    private TypeBuilder typeMembers(MemberDefinition... values) {
      typeMembers = List.of(values);
      return this;
    }

    private TypeDefinition build() {
      Symbol symbol =
          new Symbol(
              SymbolId.builtin("type/" + name),
              name,
              SymbolKind.TYPE,
              category == null
                  ? declared(name)
                  : SemanticType.declared("std.core." + name, name, List.of(), category),
              Optional.empty(),
              Optional.empty(),
              parameters.stream()
                  .map(value -> new TypeParameterInfo(value, parameter(name, value)))
                  .toList(),
              List.of(),
              documentation(name));
      return new TypeDefinition(
          symbol,
          parameters,
          shape,
          Optional.ofNullable(constructor),
          Optional.ofNullable(collectionLiteral),
          defaultCollectionLiteral,
          Optional.ofNullable(iterable),
          Optional.ofNullable(index),
          members,
          typeMembers);
    }
  }
}
