package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.DocumentId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DeclarationCatalog {
  private final List<Syntax.Program> programs;
  private final Set<DocumentId> exportedSources;
  private final Map<String, List<Syntax.FunctionDecl>> functions = new LinkedHashMap<>();
  private final Map<String, Syntax.ClassDecl> classes = new LinkedHashMap<>();
  private final Map<String, Syntax.EnumDecl> enums = new LinkedHashMap<>();
  private final Map<String, Syntax.InterfaceDecl> interfaces = new LinkedHashMap<>();
  private final Map<Object, Syntax.Program> owners = new IdentityHashMap<>();

  DeclarationCatalog(List<Syntax.Program> programs, Set<DocumentId> exportedSources) {
    this.programs = List.copyOf(programs);
    this.exportedSources = Set.copyOf(exportedSources);
    indexOwners();
  }

  boolean addInterface(Syntax.Program program, Syntax.InterfaceDecl declaration) {
    return interfaces.putIfAbsent(
            key(program, declaration.name(), declaration.visibility()), declaration)
        == null;
  }

  boolean addEnum(Syntax.Program program, Syntax.EnumDecl declaration) {
    return enums.putIfAbsent(
            key(program, declaration.name(), declaration.visibility()), declaration)
        == null;
  }

  boolean addClass(Syntax.Program program, Syntax.ClassDecl declaration) {
    return classes.putIfAbsent(
            key(program, declaration.name(), declaration.visibility()), declaration)
        == null;
  }

  boolean addFunction(Syntax.Program program, Syntax.FunctionDecl declaration) {
    List<Syntax.FunctionDecl> overloads =
        functions.computeIfAbsent(
            key(program, declaration.name(), declaration.visibility()),
            ignored -> new ArrayList<>());
    boolean unique =
        overloads.stream()
            .noneMatch(candidate -> signature(candidate).equals(signature(declaration)));
    overloads.add(declaration);
    return unique;
  }

  Syntax.Program owner(Object declaration) {
    return owners.get(declaration);
  }

  Syntax.Program ownerOr(Object declaration, Syntax.Program fallback) {
    return owners.getOrDefault(declaration, fallback);
  }

  List<Syntax.FunctionDecl> functions(String qualifiedName) {
    List<Syntax.FunctionDecl> declarations = functions.get(qualifiedName);
    return declarations == null ? List.of() : List.copyOf(declarations);
  }

  Object declaration(String qualifiedName) {
    List<Syntax.FunctionDecl> overloads = functions.get(qualifiedName);
    if (overloads != null && !overloads.isEmpty()) return overloads.getFirst();
    Object declaration = classes.get(qualifiedName);
    if (declaration == null) declaration = enums.get(qualifiedName);
    if (declaration == null) declaration = interfaces.get(qualifiedName);
    return declaration;
  }

  List<Syntax.InterfaceDecl> interfaces() {
    return List.copyOf(interfaces.values());
  }

  List<List<Syntax.FunctionDecl>> functionGroups() {
    return functions.values().stream().map(List::copyOf).toList();
  }

  Syntax.FunctionDecl resolveFunction(Syntax.Program program, String name) {
    List<Syntax.FunctionDecl> candidates = resolveFunctions(program, name);
    return candidates.isEmpty() ? null : candidates.getFirst();
  }

  List<Syntax.FunctionDecl> resolveFunctions(Syntax.Program program, String name) {
    if (program == null) return List.of();
    List<Syntax.FunctionDecl> visible = new ArrayList<>();
    visible.addAll(functions(localIdentity(qualified(program.packageName(), name), program)));
    visible.addAll(functions(qualified(program.packageName(), name)));
    if (!visible.isEmpty()) return List.copyOf(visible);
    for (Syntax.ImportDecl imported : program.imports()) {
      if (!imported.localName().equals(name)) continue;
      return functions(imported.qualifiedName()).stream()
          .filter(candidate -> canImport(program, candidate))
          .toList();
    }
    return List.of();
  }

  Syntax.ClassDecl resolveClass(Syntax.Program program, String name) {
    return resolve(program, name, classes);
  }

  Syntax.EnumDecl resolveEnum(Syntax.Program program, String name) {
    return resolve(program, name, enums);
  }

  Syntax.InterfaceDecl resolveInterface(Syntax.Program program, String name) {
    return resolve(program, name, interfaces);
  }

  Syntax.ClassDecl importedClassByDeclaredName(Syntax.Program program, String name) {
    if (program == null) return null;
    for (Syntax.ImportDecl imported : program.imports()) {
      Syntax.ClassDecl candidate = classes.get(imported.qualifiedName());
      if (candidate != null && candidate.name().equals(name) && canImport(program, candidate)) {
        return candidate;
      }
    }
    return null;
  }

  Syntax.ClassDecl resolveClass(SemanticType type) {
    return resolve(type, classes);
  }

  Syntax.EnumDecl resolveEnum(SemanticType type) {
    return resolve(type, enums);
  }

  Syntax.InterfaceDecl resolveInterface(SemanticType type) {
    return resolve(type, interfaces);
  }

  Syntax.ClassDecl ownerOf(Syntax.FunctionDecl method) {
    for (Syntax.Program program : programs) {
      for (Syntax.ClassDecl declaration : program.classes()) {
        if (declaration.methods().stream().anyMatch(candidate -> candidate == method)) {
          return declaration;
        }
      }
    }
    return null;
  }

  boolean canImport(Syntax.Program importer, Object declaration) {
    Syntax.Program owner = owners.get(declaration);
    return owner != null
        && (owner.packageName().equals(importer.packageName())
            || exportedSources.contains(owner.span().source().id()));
  }

  private void indexOwners() {
    for (Syntax.Program program : programs) {
      for (Syntax.InterfaceDecl declaration : program.interfaces()) {
        owners.put(declaration, program);
        declaration.methods().forEach(method -> owners.put(method, program));
      }
      program.enums().forEach(declaration -> owners.put(declaration, program));
      for (Syntax.ClassDecl declaration : program.classes()) {
        owners.put(declaration, program);
        declaration.fields().forEach(field -> owners.put(field, program));
        declaration.methods().forEach(method -> owners.put(method, program));
      }
      program.functions().forEach(declaration -> owners.put(declaration, program));
    }
  }

  private <T> T resolve(Syntax.Program program, String name, Map<String, T> declarations) {
    if (program == null) return null;
    T local = declarations.get(localIdentity(qualified(program.packageName(), name), program));
    if (local != null) return local;
    T samePackage = declarations.get(qualified(program.packageName(), name));
    if (samePackage != null) return samePackage;
    for (Syntax.ImportDecl imported : program.imports()) {
      if (!imported.localName().equals(name)) continue;
      T declaration = declarations.get(imported.qualifiedName());
      return declaration != null && canImport(program, declaration) ? declaration : null;
    }
    return null;
  }

  private <T> T resolve(SemanticType type, Map<String, T> declarations) {
    for (T candidate : declarations.values()) {
      Syntax.Program owner = owners.get(candidate);
      String name;
      Syntax.Visibility visibility;
      if (candidate instanceof Syntax.ClassDecl declaration) {
        name = declaration.name();
        visibility = declaration.visibility();
      } else if (candidate instanceof Syntax.EnumDecl declaration) {
        name = declaration.name();
        visibility = declaration.visibility();
      } else if (candidate instanceof Syntax.InterfaceDecl declaration) {
        name = declaration.name();
        visibility = declaration.visibility();
      } else {
        throw new IllegalStateException("unsupported declaration kind");
      }
      String identity = qualified(owner.packageName(), name);
      if (visibility == Syntax.Visibility.PRIVATE) identity = localIdentity(identity, owner);
      if (identity.equals(type.identity())) return candidate;
    }
    return null;
  }

  private static String key(Syntax.Program program, String name, Syntax.Visibility visibility) {
    String qualified = qualified(program.packageName(), name);
    return visibility == Syntax.Visibility.PRIVATE ? localIdentity(qualified, program) : qualified;
  }

  private static String qualified(String packageName, String name) {
    return packageName.isEmpty() ? name : packageName + "." + name;
  }

  private static String localIdentity(String qualified, Syntax.Program program) {
    return qualified + "@" + program.span().source().id().uri();
  }

  private static String signature(Syntax.FunctionDecl function) {
    Map<String, String> typeParameters = new HashMap<>();
    for (int index = 0; index < function.typeParameters().size(); index++) {
      typeParameters.put(function.typeParameters().get(index).name(), "$" + index);
    }
    return function.name()
        + "("
        + function.parameters().stream()
            .map(parameter -> normalizedType(parameter.type(), typeParameters))
            .collect(java.util.stream.Collectors.joining(","))
        + ")";
  }

  private static String normalizedType(Syntax.TypeRef type, Map<String, String> typeParameters) {
    String name = typeParameters.getOrDefault(type.name(), type.name());
    String arguments =
        type.arguments().isEmpty()
            ? ""
            : type.arguments().stream()
                .map(argument -> normalizedType(argument, typeParameters))
                .collect(java.util.stream.Collectors.joining(",", "<", ">"));
    return name + arguments + (type.nullable() ? "?" : "");
  }
}
