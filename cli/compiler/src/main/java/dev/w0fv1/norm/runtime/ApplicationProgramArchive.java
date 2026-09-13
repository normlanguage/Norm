package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.core.store.PortableObjectCodec;
import java.io.IOException;
import java.nio.file.Path;

public final class ApplicationProgramArchive {
  private ApplicationProgramArchive() {}

  public static void write(ApplicationProgramData application, Path destination)
      throws IOException {
    PortableObjectCodec.write(application, destination);
  }

  public static ApplicationProgramData read(Path source) throws IOException {
    return PortableObjectCodec.read(source, ApplicationProgramData.class);
  }
}
