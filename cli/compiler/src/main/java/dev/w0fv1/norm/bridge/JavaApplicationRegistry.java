package dev.w0fv1.norm.bridge;

import java.util.Set;

public interface JavaApplicationRegistry extends JavaDirectCallRegistry {
  int FORMAT = 1;

  int format();

  Set<String> types();
}
