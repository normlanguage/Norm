package dev.w0fv1.norm.cli.controller;

import java.io.PrintWriter;
import java.util.List;

interface Command {
  String name();

  String summary();

  int execute(List<String> arguments, PrintWriter out, PrintWriter err);
}
