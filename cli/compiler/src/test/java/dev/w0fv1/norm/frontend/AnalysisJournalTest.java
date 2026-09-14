package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class AnalysisJournalTest {
  @Test
  void nestedRollbackRestoresIdentityKeysReplacementsAndAppends() {
    var journal = new AnalysisJournal();
    var map = new IdentityHashMap<String, String>();
    String first = new String("same");
    String second = new String("same");
    map.put(first, "original");
    var set = new LinkedHashSet<>(Set.of("existing"));
    var list = new ArrayList<>(List.of("existing"));
    var outer = journal.checkpoint();
    journal.put(map, first, "outer");
    journal.put(map, second, "separate");
    journal.add(set, "existing");
    journal.add(set, "outer");
    journal.add(list, "outer");
    var inner = journal.checkpoint();
    journal.put(map, first, "inner");
    journal.add(list, "inner");
    assertThrows(IllegalStateException.class, () -> journal.restore(outer));
    journal.restore(inner);
    assertEquals("outer", map.get(first));
    assertEquals(List.of("existing", "outer"), list);
    journal.restore(outer);
    assertEquals("original", map.get(first));
    assertFalse(map.containsKey(second));
    assertEquals(Set.of("existing"), set);
    assertEquals(List.of("existing"), list);
    journal.put(map, first, "committed");
    var next = journal.checkpoint();
    journal.put(map, first, "temporary");
    journal.restore(next);
    assertEquals("committed", map.get(first));
  }
}
