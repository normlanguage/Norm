package dev.w0fv1.norm.utils;

import com.oracle.truffle.api.Truffle;

public final class BackendInfo {
  private BackendInfo() {}

  public static String runtimeName() {
    return Truffle.getRuntime().getName();
  }
}
