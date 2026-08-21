package dev.w0fv1.norm.semantic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuiltinSymbols {
  private static final String[] TYPE_NAMES = {
    "int",
    "bool",
    "String",
    "void",
    "Array",
    "List",
    "Map",
    "Set",
    "Stack",
    "Queue",
    "Deque",
    "Pair",
    "Range",
    "StringBuilder"
  };

  private final Map<SymbolId, Symbol> symbols;
  private final Map<String, SymbolId> globals;
  private final Map<String, SymbolId> types;
  private final Map<SymbolId, List<SymbolId>> members;

  public BuiltinSymbols() {
    Map<SymbolId, Symbol> all = new LinkedHashMap<>();
    Map<String, SymbolId> globalNames = new LinkedHashMap<>();
    Map<String, SymbolId> typeNames = new LinkedHashMap<>();
    Map<SymbolId, List<SymbolId>> memberIds = new LinkedHashMap<>();
    for (String name : TYPE_NAMES) {
      Symbol type = type(name, documentation(name));
      all.put(type.id(), type);
      typeNames.put(name, type.id());
    }
    addGlobal(all, globalNames, callable("print", "void", parameter("value", "value")));
    addGlobal(
        all,
        globalNames,
        callable("range", "Range", parameter("start", "int"), parameter("end", "int")));
    addGlobal(
        all,
        globalNames,
        callable("min", "int", parameter("left", "int"), parameter("right", "int")));
    addGlobal(
        all,
        globalNames,
        callable("max", "int", parameter("left", "int"), parameter("right", "int")));
    addGlobal(all, globalNames, callable("abs", "int", parameter("value", "int")));

    addMembers(all, memberIds, typeNames, "Array", property("Array", "length", "int"));
    addMembers(
        all,
        memberIds,
        typeNames,
        "List",
        method("List", "add", "void", parameter("value", "value")),
        method("List", "get", "value", parameter("index", "int")),
        method("List", "removeAt", "value", parameter("index", "int")),
        property("List", "length", "int"),
        method("List", "isEmpty", "bool"));
    addMembers(
        all,
        memberIds,
        typeNames,
        "Map",
        method("Map", "put", "void", parameter("key", "value"), parameter("value", "value")),
        method("Map", "get", "value", parameter("key", "value")),
        method("Map", "containsKey", "bool", parameter("key", "value")),
        method("Map", "remove", "bool", parameter("key", "value")),
        property("Map", "length", "int"),
        method("Map", "isEmpty", "bool"));
    addMembers(
        all,
        memberIds,
        typeNames,
        "Set",
        method("Set", "add", "bool", parameter("value", "value")),
        method("Set", "contains", "bool", parameter("value", "value")),
        method("Set", "remove", "bool", parameter("value", "value")),
        property("Set", "length", "int"),
        method("Set", "isEmpty", "bool"));
    addMembers(
        all,
        memberIds,
        typeNames,
        "Stack",
        method("Stack", "push", "void", parameter("value", "value")),
        method("Stack", "pop", "value"),
        method("Stack", "peek", "value"),
        property("Stack", "length", "int"),
        method("Stack", "isEmpty", "bool"));
    addMembers(
        all,
        memberIds,
        typeNames,
        "Queue",
        method("Queue", "add", "void", parameter("value", "value")),
        method("Queue", "remove", "value"),
        method("Queue", "peek", "value"),
        property("Queue", "length", "int"),
        method("Queue", "isEmpty", "bool"));
    addMembers(
        all,
        memberIds,
        typeNames,
        "Deque",
        method("Deque", "addFirst", "void", parameter("value", "value")),
        method("Deque", "addLast", "void", parameter("value", "value")),
        method("Deque", "removeFirst", "value"),
        method("Deque", "removeLast", "value"),
        method("Deque", "peekFirst", "value"),
        method("Deque", "peekLast", "value"),
        property("Deque", "length", "int"),
        method("Deque", "isEmpty", "bool"));
    addMembers(
        all,
        memberIds,
        typeNames,
        "Pair",
        field("Pair", "first", "value"),
        field("Pair", "second", "value"));
    addMembers(all, memberIds, typeNames, "Range", property("Range", "length", "int"));
    addMembers(
        all,
        memberIds,
        typeNames,
        "StringBuilder",
        method("StringBuilder", "append", "StringBuilder", parameter("value", "value")),
        method("StringBuilder", "toString", "String"),
        property("StringBuilder", "length", "int"));
    symbols = Map.copyOf(all);
    globals = Map.copyOf(globalNames);
    types = Map.copyOf(typeNames);
    Map<SymbolId, List<SymbolId>> copied = new LinkedHashMap<>();
    memberIds.forEach((owner, values) -> copied.put(owner, List.copyOf(values)));
    members = Map.copyOf(copied);
  }

  public Map<SymbolId, Symbol> symbols() {
    return symbols;
  }

  public Map<SymbolId, List<SymbolId>> members() {
    return members;
  }

  public Optional<Symbol> global(String name) {
    return Optional.ofNullable(globals.get(name)).map(symbols::get);
  }

  public Optional<Symbol> type(String name) {
    return Optional.ofNullable(types.get(name)).map(symbols::get);
  }

  public Optional<Symbol> member(String owner, String name) {
    SymbolId type = types.get(owner);
    if (type == null) return Optional.empty();
    return members.getOrDefault(type, List.of()).stream()
        .map(symbols::get)
        .filter(symbol -> symbol.name().equals(name))
        .findFirst();
  }

  public boolean isType(String name) {
    return types.containsKey(name);
  }

  public boolean isContainer(String name) {
    return List.of(
            "Array",
            "List",
            "Map",
            "Set",
            "Stack",
            "Queue",
            "Deque",
            "Pair",
            "Range",
            "StringBuilder")
        .contains(name);
  }

  public boolean isIterable(String name) {
    return List.of("Array", "List", "Set", "Range", "Queue", "Deque").contains(name);
  }

  public Optional<SemanticType> iterableElementType(String name) {
    return name.equals("Range") ? Optional.of(new SemanticType("int")) : Optional.empty();
  }

  public boolean supportsLength(String name) {
    return isContainer(name) && !name.equals("Pair");
  }

  public IndexKind indexKind(String name) {
    return switch (name) {
      case "Array", "List" -> IndexKind.INTEGER;
      case "Map" -> IndexKind.VALUE;
      default -> IndexKind.NONE;
    };
  }

  public Optional<List<ParameterInfo>> constructorParameters(String name) {
    if (name.equals("Pair")) {
      return Optional.of(List.of(parameter("first", "value"), parameter("second", "value")));
    }
    if (name.equals("Range")) {
      return Optional.of(List.of(parameter("start", "int"), parameter("end", "int")));
    }
    return isContainer(name) ? Optional.of(List.of()) : Optional.empty();
  }

  private static void addGlobal(
      Map<SymbolId, Symbol> all, Map<String, SymbolId> globals, Symbol symbol) {
    all.put(symbol.id(), symbol);
    globals.put(symbol.name(), symbol.id());
  }

  private static void addMembers(
      Map<SymbolId, Symbol> all,
      Map<SymbolId, List<SymbolId>> members,
      Map<String, SymbolId> types,
      String owner,
      Symbol... values) {
    SymbolId ownerId = types.get(owner);
    List<SymbolId> ids = new ArrayList<>();
    for (Symbol value : values) {
      all.put(value.id(), value);
      ids.add(value.id());
    }
    members.put(ownerId, ids);
  }

  private static Symbol type(String name, String documentation) {
    return new Symbol(
        SymbolId.builtin("type/" + name),
        name,
        SymbolKind.TYPE,
        new SemanticType(name),
        Optional.empty(),
        Optional.empty(),
        List.of(),
        documentation);
  }

  private static Symbol callable(String name, String result, ParameterInfo... parameters) {
    return new Symbol(
        SymbolId.builtin("function/" + name),
        name,
        SymbolKind.FUNCTION,
        new SemanticType(result),
        Optional.empty(),
        Optional.empty(),
        List.of(parameters),
        documentation(name));
  }

  private static Symbol method(
      String owner, String name, String result, ParameterInfo... parameters) {
    return member(owner, name, SymbolKind.METHOD, result, parameters);
  }

  private static Symbol property(String owner, String name, String type) {
    return member(owner, name, SymbolKind.PROPERTY, type);
  }

  private static Symbol field(String owner, String name, String type) {
    return member(owner, name, SymbolKind.FIELD, type);
  }

  private static Symbol member(
      String owner, String name, SymbolKind kind, String type, ParameterInfo... parameters) {
    return new Symbol(
        SymbolId.builtin("member/" + owner + "/" + name),
        name,
        kind,
        new SemanticType(type),
        Optional.empty(),
        Optional.of(SymbolId.builtin("type/" + owner)),
        List.of(parameters),
        "Norm " + owner + " " + kind.name().toLowerCase());
  }

  private static ParameterInfo parameter(String name, String type) {
    return new ParameterInfo(name, new SemanticType(type));
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
}
