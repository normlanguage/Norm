package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import java.util.Map;

record ExecutableProgram(RootCallTarget entryPoint, Map<String, RootCallTarget> functions) {
  ExecutableProgram {
    functions = Map.copyOf(functions);
  }
}
