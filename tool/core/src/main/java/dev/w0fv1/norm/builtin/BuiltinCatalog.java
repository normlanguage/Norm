package dev.w0fv1.norm.builtin;

import dev.w0fv1.norm.semantic.IndexKind;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
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
  private final Map<SymbolId, TypeDefinition> typesBySymbol;
  private final Map<String, GlobalDefinition> globals;
  private final Map<SymbolId, Symbol> symbols;
  private final Map<SymbolId, IntrinsicId> intrinsics;
  private final Map<SymbolId, IntrinsicId> writeIntrinsics;
  private final Map<SymbolId, List<SymbolId>> members;

  private BuiltinCatalog(Map<String, TypeDefinition> types, Map<String, GlobalDefinition> globals) {
    this.types = Map.copyOf(types);
    Map<SymbolId, TypeDefinition> indexedTypes = new LinkedHashMap<>();
    types.values().forEach(type -> indexedTypes.put(type.symbol().id(), type));
    typesBySymbol = Map.copyOf(indexedTypes);
    this.globals = Map.copyOf(globals);
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
      allMembers.put(type.symbol().id(), List.copyOf(memberIds));
    }
    for (GlobalDefinition global : globals.values()) {
      putUnique(allSymbols, global.symbol());
      allIntrinsics.put(global.symbol().id(), global.intrinsic());
    }
    symbols = Map.copyOf(allSymbols);
    intrinsics = Map.copyOf(allIntrinsics);
    writeIntrinsics = Map.copyOf(allWriteIntrinsics);
    members = Map.copyOf(allMembers);
    if (!declaredIntrinsics().equals(Set.copyOf(EnumSet.allOf(IntrinsicId.class)))) {
      throw new IllegalStateException("builtin catalog does not declare every intrinsic");
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
    return Optional.ofNullable(types.get(name));
  }

  public Optional<TypeDefinition> type(SymbolId symbol) {
    return Optional.ofNullable(typesBySymbol.get(symbol));
  }

  public Optional<GlobalDefinition> global(String name) {
    return Optional.ofNullable(globals.get(name));
  }

  public Optional<MemberDefinition> member(String owner, String name) {
    TypeDefinition type = types.get(owner);
    if (type == null) return Optional.empty();
    return type.members().stream()
        .filter(member -> member.symbol().name().equals(name))
        .findFirst();
  }

  public Optional<Symbol> member(SemanticType owner, String name) {
    return member(owner.name(), name)
        .map(MemberDefinition::symbol)
        .map(symbol -> substitute(symbol, substitutions(types.get(owner.name()), owner)));
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
    }
    return Set.copyOf(result);
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

  private static BuiltinCatalog create() {
    Map<String, TypeDefinition> types = new LinkedHashMap<>();
    Map<String, GlobalDefinition> globals = new LinkedHashMap<>();
    SemanticType intType = declared("int");
    SemanticType boolType = declared("bool");
    SemanticType stringType = declared("String");
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

    addType(types, type("int", RuntimeShape.INT));
    addType(types, type("bool", RuntimeShape.BOOL));
    addType(types, type("void", RuntimeShape.VOID));
    addType(
        types,
        type("String", RuntimeShape.STRING)
            .members(
                method("String", "byteSize", intType, IntrinsicId.STRING_BYTE_SIZE),
                method("String", "codePointSize", intType, IntrinsicId.STRING_CODE_POINT_SIZE),
                method("String", "graphemeSize", intType, IntrinsicId.STRING_GRAPHEME_SIZE)));
    addType(
        types,
        type("Array", RuntimeShape.ARRAY, "T")
            .constructor(IntrinsicId.ARRAY_CONSTRUCT)
            .iterable(parameter("Array", "T"), IntrinsicId.ARRAY_ITERATOR)
            .index(
                IndexKind.INTEGER,
                intType,
                parameter("Array", "T"),
                IntrinsicId.ARRAY_INDEX_READ,
                IntrinsicId.ARRAY_INDEX_WRITE)
            .members(method("Array", "size", intType, IntrinsicId.SIZE)));
    addType(
        types,
        type("List", RuntimeShape.LIST, "T")
            .constructor(IntrinsicId.LIST_CONSTRUCT)
            .iterable(listT, IntrinsicId.LIST_ITERATOR)
            .index(
                IndexKind.INTEGER,
                intType,
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
                method("List", "get", listT, IntrinsicId.LIST_GET, parameterInfo("index", intType)),
                method(
                    "List",
                    "removeAt",
                    listT,
                    IntrinsicId.LIST_REMOVE_AT,
                    parameterInfo("index", intType)),
                method("List", "size", intType, IntrinsicId.SIZE),
                method("List", "isEmpty", boolType, IntrinsicId.IS_EMPTY)));
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
                    "Map",
                    "containsKey",
                    boolType,
                    IntrinsicId.MAP_CONTAINS_KEY,
                    parameterInfo("key", mapK)),
                method(
                    "Map", "remove", boolType, IntrinsicId.MAP_REMOVE, parameterInfo("key", mapK)),
                method("Map", "size", intType, IntrinsicId.SIZE),
                method("Map", "isEmpty", boolType, IntrinsicId.IS_EMPTY)));
    addType(
        types,
        type("Set", RuntimeShape.SET, "T")
            .constructor(IntrinsicId.SET_CONSTRUCT)
            .iterable(setT, IntrinsicId.SET_ITERATOR)
            .members(
                method("Set", "add", boolType, IntrinsicId.SET_ADD, parameterInfo("value", setT)),
                method(
                    "Set",
                    "contains",
                    boolType,
                    IntrinsicId.SET_CONTAINS,
                    parameterInfo("value", setT)),
                method(
                    "Set",
                    "remove",
                    boolType,
                    IntrinsicId.SET_REMOVE,
                    parameterInfo("value", setT)),
                method("Set", "size", intType, IntrinsicId.SIZE),
                method("Set", "isEmpty", boolType, IntrinsicId.IS_EMPTY)));
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
                method("Stack", "size", intType, IntrinsicId.SIZE),
                method("Stack", "isEmpty", boolType, IntrinsicId.IS_EMPTY)));
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
                method("Queue", "size", intType, IntrinsicId.SIZE),
                method("Queue", "isEmpty", boolType, IntrinsicId.IS_EMPTY)));
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
                method("Deque", "size", intType, IntrinsicId.SIZE),
                method("Deque", "isEmpty", boolType, IntrinsicId.IS_EMPTY)));
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
                parameterInfo("start", intType),
                parameterInfo("end", intType))
            .iterable(intType, IntrinsicId.RANGE_ITERATOR)
            .members(method("Range", "size", intType, IntrinsicId.SIZE)));
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
                method("StringBuilder", "size", intType, IntrinsicId.SIZE)));

    addGlobal(
        globals,
        global(
            "print",
            SemanticType.VOID,
            IntrinsicId.PRINT,
            parameterInfo("value", SemanticType.DYNAMIC)));
    addGlobal(
        globals,
        global(
            "range",
            rangeType,
            IntrinsicId.RANGE_CONSTRUCT,
            parameterInfo("start", intType),
            parameterInfo("end", intType)));
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
        SymbolId.builtin("member/" + owner + "/" + name),
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
            SymbolId.builtin("function/" + name),
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

  private static void addType(Map<String, TypeDefinition> values, TypeBuilder builder) {
    TypeDefinition definition = builder.build();
    if (values.putIfAbsent(definition.symbol().name(), definition) != null) {
      throw new IllegalStateException("duplicate builtin type " + definition.symbol().name());
    }
  }

  private static void addGlobal(Map<String, GlobalDefinition> values, GlobalDefinition value) {
    if (values.putIfAbsent(value.symbol().name(), value) != null) {
      throw new IllegalStateException("duplicate builtin global " + value.symbol().name());
    }
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
      case "print" -> "Writes one value followed by a newline.";
      default -> "Norm built-in.";
    };
  }

  public record TypeDefinition(
      Symbol symbol,
      List<String> typeParameters,
      RuntimeShape runtimeShape,
      Optional<ConstructorCapability> constructor,
      Optional<IterableCapability> iterable,
      Optional<IndexCapability> index,
      List<MemberDefinition> members) {
    public TypeDefinition {
      typeParameters = List.copyOf(typeParameters);
      constructor = Objects.requireNonNull(constructor);
      iterable = Objects.requireNonNull(iterable);
      index = Objects.requireNonNull(index);
      members = List.copyOf(members);
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

  public record ResolvedIndex(
      IndexKind kind,
      SemanticType keyType,
      SemanticType resultType,
      IntrinsicId readIntrinsic,
      Optional<IntrinsicId> writeIntrinsic) {}

  private static final class TypeBuilder {
    private final String name;
    private final RuntimeShape shape;
    private final List<String> parameters;
    private ConstructorCapability constructor;
    private IterableCapability iterable;
    private IndexCapability index;
    private List<MemberDefinition> members = List.of();

    private TypeBuilder(String name, RuntimeShape shape, List<String> parameters) {
      this.name = name;
      this.shape = shape;
      this.parameters = parameters;
    }

    private TypeBuilder constructor(IntrinsicId intrinsic, ParameterInfo... parameters) {
      constructor = new ConstructorCapability(List.of(parameters), intrinsic);
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

    private TypeDefinition build() {
      Symbol symbol =
          new Symbol(
              SymbolId.builtin("type/" + name),
              name,
              SymbolKind.TYPE,
              declared(name),
              Optional.empty(),
              Optional.empty(),
              parameters,
              List.of(),
              documentation(name));
      return new TypeDefinition(
          symbol,
          parameters,
          shape,
          Optional.ofNullable(constructor),
          Optional.ofNullable(iterable),
          Optional.ofNullable(index),
          members);
    }
  }
}
