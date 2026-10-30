package dev.w0fv1.norm.cli.update;

import java.nio.file.Path;

public final class UpdateInstallerProcess {
  public static void main(String[] arguments) throws Exception {
    new SystemUpdateHost().install(Path.of(arguments[0]), Path.of(arguments[1]));
    System.out.println("scheduled");
    System.in.read();
  }
}
