package dev.w0fv1.norm.documentation;

import java.util.List;

public final class MissingDocumentationException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final transient List<String> declarations;

  public MissingDocumentationException(List<String> declarations) {
    super("exported declarations are missing @Document");
    this.declarations = List.copyOf(declarations);
  }

  public List<String> declarations() {
    return declarations;
  }
}
