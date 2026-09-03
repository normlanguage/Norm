package dev.w0fv1.norm.value;

public final class ModuleArchiveFormat {
  public static final String EXTENSION = "nar";
  public static final String FILE_SUFFIX = "." + EXTENSION;
  public static final int FORMAT_VERSION = 5;
  public static final int MINIMUM_READABLE_VERSION = 4;

  public static boolean isReadable(int version) {
    return version >= MINIMUM_READABLE_VERSION && version <= FORMAT_VERSION;
  }

  private ModuleArchiveFormat() {}
}
