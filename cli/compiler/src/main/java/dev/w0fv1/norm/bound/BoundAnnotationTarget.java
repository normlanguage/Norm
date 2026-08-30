package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.DocumentId;
import java.util.Objects;

public sealed interface BoundAnnotationTarget
    permits BoundAnnotationTarget.Package,
        BoundAnnotationTarget.Definition,
        BoundAnnotationTarget.Field,
        BoundAnnotationTarget.Parameter,
        BoundAnnotationTarget.Local {
  AnnotationTarget kind();

  record Package(DocumentId document, String packageName) implements BoundAnnotationTarget {
    public Package {
      Objects.requireNonNull(document, "document");
      Objects.requireNonNull(packageName, "packageName");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.PACKAGE;
    }
  }

  record Definition(AnnotationTarget kind, String id) implements BoundAnnotationTarget {
    public Definition {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(id, "id");
      if (kind != AnnotationTarget.TYPE
          && kind != AnnotationTarget.CONSTRUCTOR
          && kind != AnnotationTarget.FUNCTION) {
        throw new IllegalArgumentException("invalid definition annotation target");
      }
    }
  }

  record Field(BoundFieldId field) implements BoundAnnotationTarget {
    public Field {
      Objects.requireNonNull(field, "field");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.FIELD;
    }
  }

  record Parameter(String owner, int ordinal) implements BoundAnnotationTarget {
    public Parameter {
      Objects.requireNonNull(owner, "owner");
      if (ordinal < 0) throw new IllegalArgumentException("parameter ordinal must not be negative");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.PARAMETER;
    }
  }

  record Local(BoundCallableId owner, BoundLocalId local) implements BoundAnnotationTarget {
    public Local {
      Objects.requireNonNull(owner, "owner");
      Objects.requireNonNull(local, "local");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.LOCAL;
    }
  }
}
