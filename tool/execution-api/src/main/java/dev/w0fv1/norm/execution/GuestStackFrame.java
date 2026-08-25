package dev.w0fv1.norm.execution;

import java.io.Serializable;
import java.net.URI;

public record GuestStackFrame(String name, URI uri, int line, int column) implements Serializable {}
