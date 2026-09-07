package dev.w0fv1.norm.builtin;

import dev.w0fv1.norm.abi.BuiltinContracts;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.abi.RuntimeShape;
import dev.w0fv1.norm.semantic.IndexKind;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.TypeConstraintSolver;
import dev.w0fv1.norm.semantic.TypeParameterInfo;
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

  private final List<ProtocolConformance> protocolConformances =
      BuiltinContracts.standard().protocolConformances().stream()
          .map(BuiltinSemanticView::conformance)
          .toList();
  private final Map<String, TypeDefinition> types;
  private final Map<String, TypeDefinition> hiddenTypes;
  private final Map<SymbolId, TypeDefinition> typesBySymbol;
  private final Map<String, List<GlobalDefinition>> globals;
  private final Map<SymbolId, Symbol> symbols;
  private final Map<SymbolId, IntrinsicId> intrinsics;
  private final Map<SymbolId, IntrinsicId> writeIntrinsics;
  private final Map<SymbolId, List<SymbolId>> members;

  private BuiltinCatalog(
      Map<String, TypeDefinition> types,
      Map<String, TypeDefinition> hiddenTypes,
      Map<String, List<GlobalDefinition>> globals) {
    this.types = Map.copyOf(types);
    this.hiddenTypes = Map.copyOf(hiddenTypes);
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

  public Set<String> typeNames() {
    return types.keySet();
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
        .map(symbol -> symbol.substitute(substitutions(types.get(owner.name()), owner)));
  }

  public List<Symbol> members(SemanticType owner, String name) {
    Map<String, SemanticType> substitutions = substitutions(types.get(owner.name()), owner);
    return members(owner.name(), name).stream()
        .map(MemberDefinition::symbol)
        .map(symbol -> symbol.substitute(substitutions))
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
        .map(symbol -> symbol.substitute(substitutions));
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
    return protocolConformances.stream()
        .filter(conformance -> conformance.concreteType().identity().equals(type.identity()))
        .filter(conformance -> conformance.typeParameters().size() == type.arguments().size())
        .map(
            conformance -> {
              Map<String, SemanticType> substitutions = new LinkedHashMap<>();
              for (int index = 0; index < type.arguments().size(); index++) {
                substitutions.put(
                    conformance.typeParameters().get(index).identity(),
                    type.arguments().get(index));
              }
              return conformance.interfaceType().substitute(substitutions);
            })
        .toList();
  }

  public List<ProtocolConformance> protocolConformances() {
    return protocolConformances;
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
                    new ParameterInfo(
                        parameter.name(),
                        parameter.type().substitute(substitutions),
                        parameter.hasDefault()))
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
    Map<String, TypeDefinition> hiddenTypes = new LinkedHashMap<>();
    Map<String, List<GlobalDefinition>> globals = new LinkedHashMap<>();
    for (BuiltinContracts.TypeDefinition type : BuiltinContracts.standard().types()) {
      (type.hidden() ? hiddenTypes : types)
          .put(type.symbol().name(), BuiltinSemanticView.definition(type));
    }
    for (BuiltinContracts.GlobalDefinition function : BuiltinContracts.standard().globals()) {
      GlobalDefinition global =
          new GlobalDefinition(
              BuiltinSemanticView.symbol(function.symbol(), Optional.empty()),
              function.intrinsic());
      globals.computeIfAbsent(global.symbol().name(), ignored -> new ArrayList<>()).add(global);
    }
    return new BuiltinCatalog(types, hiddenTypes, globals);
  }

  private static void putUnique(Map<SymbolId, Symbol> values, Symbol value) {
    if (values.putIfAbsent(value.id(), value) != null) {
      throw new IllegalStateException("duplicate builtin symbol " + value.id().value());
    }
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
}
