package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreAnnotationReferenceSite(Owner owner, CoreAnnotationPath path) {
  public CoreAnnotationReferenceSite {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(path, "path");
  }

  public sealed interface Owner permits Definition, Metadata {}

  public record Definition(DefinitionOccurrenceId occurrence) implements Owner {
    public Definition {
      Objects.requireNonNull(occurrence, "occurrence");
    }
  }

  public record Metadata(CoreAnnotationApplicationId application) implements Owner {
    public Metadata {
      Objects.requireNonNull(application, "application");
    }
  }

  public CoreAnnotationReferenceSite child(CoreAnnotationPath.Kind kind, int index) {
    return new CoreAnnotationReferenceSite(owner, path.child(kind, index));
  }
}
