package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;

record ExecutableProgram(RootCallTarget entryPoint) {}
