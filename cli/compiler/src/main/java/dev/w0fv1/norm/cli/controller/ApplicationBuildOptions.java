package dev.w0fv1.norm.cli.controller;

import java.util.ArrayList;
import java.util.List;

record ApplicationBuildOptions(ApplicationBuildTarget target, String input) {
  static ApplicationBuildOptions parse(List<String> arguments) {
    ApplicationBuildTarget target = ApplicationBuildTarget.NATIVE;
    List<String> positional = new ArrayList<>();
    for (String argument : arguments) {
      if (argument.equals("--jvm")) {
        target = ApplicationBuildTarget.JVM;
      } else if (argument.startsWith("-")) {
        throw new IllegalArgumentException("unknown build option '" + argument + "'");
      } else {
        positional.add(argument);
      }
    }
    if (positional.size() > 1) {
      throw new IllegalArgumentException("'build' expects at most one source file or project");
    }
    return new ApplicationBuildOptions(target, positional.isEmpty() ? "." : positional.getFirst());
  }
}
