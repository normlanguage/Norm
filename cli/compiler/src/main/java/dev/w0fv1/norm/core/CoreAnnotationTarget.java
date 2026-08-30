package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.Objects;

public sealed interface CoreAnnotationTarget
    permits CoreAnnotationTarget.Package,
        CoreAnnotationTarget.Definition,
        CoreAnnotationTarget.Field,
        CoreAnnotationTarget.Parameter,
        CoreAnnotationTarget.Local {
  AnnotationTarget kind();

  record Package(ModuleCoordinate module, String packageName) implements CoreAnnotationTarget {
    public Package {
      Objects.requireNonNull(module, "module");
      Objects.requireNonNull(packageName, "packageName");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.PACKAGE;
    }
  }

  record Definition(AnnotationTarget kind, DefinitionOccurrenceId occurrence)
      implements CoreAnnotationTarget {
    public Definition {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(occurrence, "occurrence");
      if (kind != AnnotationTarget.TYPE
          && kind != AnnotationTarget.CONSTRUCTOR
          && kind != AnnotationTarget.FUNCTION) {
        throw new IllegalArgumentException("invalid definition annotation target");
      }
    }
  }

  record Field(DefinitionOccurrenceId owner, int ordinal) implements CoreAnnotationTarget {
    public Field {
      Objects.requireNonNull(owner, "owner");
      if (ordinal < 0) throw new IllegalArgumentException("field ordinal must not be negative");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.FIELD;
    }
  }

  record Parameter(DefinitionOccurrenceId callable, int index) implements CoreAnnotationTarget {
    public Parameter {
      Objects.requireNonNull(callable, "callable");
      if (index < 0) throw new IllegalArgumentException("parameter index must not be negative");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.PARAMETER;
    }
  }

  record Local(DefinitionOccurrenceId callable, int index) implements CoreAnnotationTarget {
    public Local {
      Objects.requireNonNull(callable, "callable");
      if (index < 0) throw new IllegalArgumentException("local index must not be negative");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.LOCAL;
    }
  }
}
