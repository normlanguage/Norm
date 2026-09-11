package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreType;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

final class ExecutionContexts {
  private final ScopedValue<Map<CoreType, Object>> current = ScopedValue.newInstance();

  Object get(CoreType type) {
    return current.isBound()
        ? current.get().getOrDefault(type, RuntimeValues.NullValue.INSTANCE)
        : RuntimeValues.NullValue.INSTANCE;
  }

  <T> T with(CoreType type, Object value, Supplier<T> action) {
    var values = new HashMap<CoreType, Object>();
    if (current.isBound()) values.putAll(current.get());
    values.put(type, value);
    return ScopedValue.where(current, Map.copyOf(values)).call(action::get);
  }
}
