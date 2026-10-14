package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundCallableId;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DefaultValueDeclarations {
  private final Map<SymbolId, Declaration> declarations = new LinkedHashMap<>();
  private final Map<SymbolId, Factory> initializers = new LinkedHashMap<>();
  private final Map<SymbolId, List<Optional<BoundCallableId>>> constructionDefaults =
      new LinkedHashMap<>();

  DefaultValueDeclarations(List<Syntax.Program> programs, SemanticModel semantics) {
    for (var program : programs) {
      for (var function : program.functions()) {
        add(
            semantics,
            function.nameSpan(),
            List.of(),
            function.typeParameters(),
            Optional.empty(),
            function.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
      }
      for (var owner : program.aggregates()) {
        var symbol = semantics.symbolOf(owner.nameSpan()).orElseThrow();
        var receiver = receiver(semantics, symbol, owner.typeParameters());
        for (var field : owner.fields()) {
          field
              .defaultValue()
              .ifPresent(
                  expression -> {
                    var fieldSymbol = semantics.symbolOf(field.nameSpan()).orElseThrow();
                    initializers.put(
                        fieldSymbol.id(),
                        new Factory(
                            new BoundCallableId(fieldSymbol.id().value() + "/initializer"),
                            field.name() + "/initializer",
                            owner.typeParameters(),
                            Optional.of(receiver),
                            fieldSymbol.type(),
                            expression));
                  });
        }
        if (owner.constructors().isEmpty()) {
          constructionDefaults.put(
              symbol.id(),
              owner.fields().stream()
                  .filter(
                      field ->
                          symbol.parameters().stream()
                              .anyMatch(parameter -> parameter.name().equals(field.name())))
                  .map(
                      field -> initializer(semantics.symbolOf(field.nameSpan()).orElseThrow().id()))
                  .toList());
        } else {
          for (var constructor : owner.constructors()) {
            add(
                semantics,
                constructor.nameSpan(),
                owner.typeParameters(),
                List.of(),
                Optional.empty(),
                constructor.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
          }
        }
        for (var method : owner.methods()) {
          add(
              semantics,
              method.nameSpan(),
              owner.typeParameters(),
              method.typeParameters(),
              Optional.of(receiver),
              method.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
        }
      }
      for (var owner : program.interfaces()) {
        var receiver =
            receiver(
                semantics,
                semantics.symbolOf(owner.nameSpan()).orElseThrow(),
                owner.typeParameters());
        for (var method : owner.methods()) {
          add(
              semantics,
              method.nameSpan(),
              owner.typeParameters(),
              method.typeParameters(),
              Optional.of(receiver),
              method.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
        }
      }
      for (var owner : program.enums()) {
        for (var variant : owner.variants()) {
          add(
              semantics,
              variant.nameSpan(),
              owner.typeParameters(),
              List.of(),
              Optional.empty(),
              variant.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
        }
      }
    }
  }

  List<Factory> factories() {
    var factories = new ArrayList<Factory>();
    for (var declaration : declarations.values()) {
      for (int ordinal = 0; ordinal < declaration.defaults().size(); ordinal++) {
        if (declaration.defaults().get(ordinal).isEmpty()) continue;
        factories.add(
            new Factory(
                declaration.factory(ordinal),
                declaration.symbol().name() + "/argument/" + ordinal,
                declaration.typeParameters(),
                declaration.receiver(),
                declaration.symbol().parameters().get(ordinal).type(),
                declaration.defaults().get(ordinal).orElseThrow()));
      }
    }
    factories.addAll(initializers.values());
    return List.copyOf(factories);
  }

  Optional<BoundCallableId> initializer(SymbolId field) {
    return Optional.ofNullable(initializers.get(field)).map(Factory::id);
  }

  Optional<Declaration> declaration(SymbolId target) {
    return Optional.ofNullable(declarations.get(target));
  }

  Optional<BoundCallableId> defaultValue(SymbolId target, int parameter) {
    if (constructionDefaults.containsKey(target))
      return constructionDefaults.get(target).get(parameter);
    return declaration(target)
        .flatMap(
            value -> value.defaults().get(parameter).map(expression -> value.factory(parameter)));
  }

  private void add(
      SemanticModel semantics,
      SourceSpan name,
      List<Syntax.TypeParameter> ownerParameters,
      List<Syntax.TypeParameter> callableParameters,
      Optional<SemanticType> receiver,
      List<Optional<Syntax.Expression>> defaults) {
    if (defaults.stream().noneMatch(Optional::isPresent)) return;
    var symbol = semantics.symbolOf(name).orElseThrow();
    var parameters = new ArrayList<>(ownerParameters);
    parameters.addAll(callableParameters);
    declarations.put(symbol.id(), new Declaration(symbol, parameters, receiver, defaults));
  }

  private static SemanticType receiver(
      SemanticModel semantics, Symbol owner, List<Syntax.TypeParameter> parameters) {
    return SemanticType.declared(
        owner.type().identity(),
        owner.name(),
        parameters.stream()
            .map(parameter -> semantics.symbolOf(parameter.nameSpan()).orElseThrow().type())
            .toList(),
        owner.type().category());
  }

  record Declaration(
      Symbol symbol,
      List<Syntax.TypeParameter> typeParameters,
      Optional<SemanticType> receiver,
      List<Optional<Syntax.Expression>> defaults) {
    Declaration {
      typeParameters = List.copyOf(typeParameters);
      defaults = List.copyOf(defaults);
    }

    BoundCallableId factory(int parameter) {
      return new BoundCallableId(symbol.id().value() + "/argument/" + parameter);
    }
  }

  record Factory(
      BoundCallableId id,
      String name,
      List<Syntax.TypeParameter> typeParameters,
      Optional<SemanticType> receiver,
      SemanticType returnType,
      Syntax.Expression expression) {
    Factory {
      typeParameters = List.copyOf(typeParameters);
    }
  }
}
