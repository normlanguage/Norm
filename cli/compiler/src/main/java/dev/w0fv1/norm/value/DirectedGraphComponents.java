package dev.w0fv1.norm.value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

public final class DirectedGraphComponents {
  private DirectedGraphComponents() {}

  public static List<List<Integer>> compute(
      List<int[]> dependencies, IntPredicate selected, Runnable checkpoint) {
    Tarjan tarjan = new Tarjan(dependencies, checkpoint);
    for (int vertex = 0; vertex < dependencies.size(); vertex++) {
      if (selected.test(vertex) && tarjan.indices[vertex] == -1) tarjan.visit(vertex);
    }
    return List.copyOf(tarjan.components);
  }

  private static final class Tarjan {
    private final List<int[]> dependencies;
    private final Runnable checkpoint;
    private final int[] indices;
    private final int[] lowLinks;
    private final int[] cursors;
    private final boolean[] onStack;
    private final ArrayDeque<Integer> stack = new ArrayDeque<>();
    private final ArrayDeque<Integer> traversal = new ArrayDeque<>();
    private final List<List<Integer>> components = new ArrayList<>();
    private int nextIndex;

    private Tarjan(List<int[]> dependencies, Runnable checkpoint) {
      this.dependencies = dependencies;
      this.checkpoint = checkpoint;
      indices = new int[dependencies.size()];
      Arrays.fill(indices, -1);
      lowLinks = new int[dependencies.size()];
      cursors = new int[dependencies.size()];
      onStack = new boolean[dependencies.size()];
    }

    private void enter(int declaration) {
      indices[declaration] = nextIndex;
      lowLinks[declaration] = nextIndex++;
      stack.push(declaration);
      onStack[declaration] = true;
      traversal.push(declaration);
    }

    private void visit(int root) {
      enter(root);
      while (!traversal.isEmpty()) {
        checkpoint.run();
        int declaration = traversal.peek();
        int[] targets = dependencies.get(declaration);
        if (cursors[declaration] < targets.length) {
          int dependency = targets[cursors[declaration]++];
          if (indices[dependency] == -1) enter(dependency);
          else if (onStack[dependency])
            lowLinks[declaration] = Math.min(lowLinks[declaration], indices[dependency]);
          continue;
        }
        traversal.pop();
        if (!traversal.isEmpty()) {
          int parent = traversal.peek();
          lowLinks[parent] = Math.min(lowLinks[parent], lowLinks[declaration]);
        }
        if (lowLinks[declaration] != indices[declaration]) continue;
        List<Integer> component = new ArrayList<>();
        int member;
        do {
          member = stack.pop();
          onStack[member] = false;
          component.add(member);
        } while (member != declaration);
        components.add(List.copyOf(component));
      }
    }
  }
}
