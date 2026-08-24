package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public sealed interface CoreBindingShape {
  record Callable(int typeParameterCount, List<Parameter> parameters, CoreType returnType)
      implements CoreBindingShape {
    public Callable {
      if (typeParameterCount < 0) {
        throw new IllegalArgumentException("type parameter count must not be negative");
      }
      parameters = List.copyOf(parameters);
      Objects.requireNonNull(returnType, "returnType");
    }
  }

  record Class(int typeParameterCount, List<Field> fields) implements CoreBindingShape {
    public Class {
      if (typeParameterCount < 0) {
        throw new IllegalArgumentException("type parameter count must not be negative");
      }
      fields = List.copyOf(fields);
    }
  }

  record Enum(List<String> members) implements CoreBindingShape {
    public Enum {
      members = List.copyOf(members);
      if (members.stream().anyMatch(String::isBlank)) {
        throw new IllegalArgumentException("enum member name must not be blank");
      }
    }
  }

  record Parameter(String label, CoreType type) {
    public Parameter {
      Objects.requireNonNull(label, "label");
      if (label.isBlank()) throw new IllegalArgumentException("parameter label must not be blank");
      Objects.requireNonNull(type, "type");
    }
  }

  record Field(String name, CoreVisibility visibility, CoreType type) {
    public Field {
      Objects.requireNonNull(name, "name");
      if (name.isBlank()) throw new IllegalArgumentException("field name must not be blank");
      Objects.requireNonNull(visibility, "visibility");
      Objects.requireNonNull(type, "type");
    }
  }
}
