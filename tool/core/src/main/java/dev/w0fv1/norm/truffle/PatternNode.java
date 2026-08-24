package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

abstract class PatternNode extends Node {
  abstract boolean matches(Object value, VirtualFrame frame);
}
