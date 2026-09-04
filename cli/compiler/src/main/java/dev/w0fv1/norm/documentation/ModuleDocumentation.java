package dev.w0fv1.norm.documentation;

import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ModuleDocumentation(ModuleCoordinate module, List<File> files) {
  public ModuleDocumentation {
    Objects.requireNonNull(module, "module");
    files = List.copyOf(files);
  }

  public record File(
      String sourcePath,
      String documentPath,
      String packageName,
      boolean exported,
      Optional<Document> document,
      List<Declaration> declarations) {
    public File {
      Objects.requireNonNull(sourcePath, "sourcePath");
      Objects.requireNonNull(documentPath, "documentPath");
      Objects.requireNonNull(packageName, "packageName");
      document = Objects.requireNonNull(document, "document");
      declarations = List.copyOf(declarations);
    }
  }

  public record Document(
      String description,
      List<Reference> types,
      List<Reference> functions,
      List<Reference> fields) {
    public Document {
      Objects.requireNonNull(description, "description");
      types = List.copyOf(types);
      functions = List.copyOf(functions);
      fields = List.copyOf(fields);
    }
  }

  public record Reference(String kind, String target, String display) {
    public Reference {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(display, "display");
    }
  }

  public record Declaration(
      String kind,
      String id,
      String name,
      String signature,
      String visibility,
      SourceRange source,
      List<TypeParameter> typeParameters,
      List<Parameter> parameters,
      Optional<Type> returns,
      Optional<Type> type,
      Optional<Document> document,
      List<Declaration> members) {
    public Declaration {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(signature, "signature");
      Objects.requireNonNull(visibility, "visibility");
      Objects.requireNonNull(source, "source");
      typeParameters = List.copyOf(typeParameters);
      parameters = List.copyOf(parameters);
      returns = Objects.requireNonNull(returns, "returns");
      type = Objects.requireNonNull(type, "type");
      document = Objects.requireNonNull(document, "document");
      members = List.copyOf(members);
    }
  }

  public record TypeParameter(String name, Optional<Type> upperBound, Optional<Type> defaultType) {
    public TypeParameter {
      Objects.requireNonNull(name, "name");
      upperBound = Objects.requireNonNull(upperBound, "upperBound");
      defaultType = Objects.requireNonNull(defaultType, "defaultType");
    }

    public TypeParameter(String name, Optional<Type> upperBound) {
      this(name, upperBound, Optional.empty());
    }
  }

  public record Parameter(String name, Type type, Optional<Document> document) {
    public Parameter {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
      document = Objects.requireNonNull(document, "document");
    }
  }

  public record Type(
      String kind,
      String identity,
      String name,
      String display,
      boolean nullable,
      List<Type> arguments) {
    public Type {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(identity, "identity");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(display, "display");
      arguments = List.copyOf(arguments);
    }
  }

  public record SourceRange(Position start, Position end) {
    public SourceRange {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
    }
  }

  public record Position(int line, int column) {
    public Position {
      if (line < 1 || column < 1) throw new IllegalArgumentException("source position is invalid");
    }
  }
}
