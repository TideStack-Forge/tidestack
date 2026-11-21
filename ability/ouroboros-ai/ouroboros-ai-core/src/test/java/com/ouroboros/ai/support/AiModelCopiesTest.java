package com.ouroboros.ai.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiModelCopiesTest {

  @Test
  void copyMap_returnsImmutableLinkedHashMapCopy() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("first", 1);
    source.put("second", 2);

    Map<String, Object> copied = AiModelCopies.copyMap(source);

    assertEquals(2, copied.size());
    assertEquals(Arrays.asList("first", "second"), new ArrayList<>(copied.keySet()));
    assertThrows(UnsupportedOperationException.class, () -> copied.put("third", 3));
  }

  @Test
  void copyMap_returnsEmptyMapForNullOrEmptySource() {
    assertEquals(Collections.emptyMap(), AiModelCopies.copyMap(null));
    assertEquals(Collections.emptyMap(), AiModelCopies.copyMap(Collections.<String, Object>emptyMap()));
  }

  @Test
  void copyStringList_returnsImmutableCopyAndKeepsOrder() {
    List<String> source = new ArrayList<>(Arrays.asList("alpha", "beta"));

    List<String> copied = AiModelCopies.copyStringList(source);

    assertEquals(Arrays.asList("alpha", "beta"), copied);
    assertThrows(UnsupportedOperationException.class, () -> copied.add("gamma"));
  }

  @Test
  void copyStringSet_returnsImmutableLinkedHashSetCopy() {
    Set<String> source = new LinkedHashSet<>(Arrays.asList("alpha", "beta"));

    Set<String> copied = AiModelCopies.copyStringSet(source);

    assertEquals(Arrays.asList("alpha", "beta"), new ArrayList<>(copied));
    assertThrows(UnsupportedOperationException.class, () -> copied.add("gamma"));
  }

  @Test
  void copyGenericListAndSetReturnEmptySingletonsForEmptySources() {
    List<String> copiedList = AiModelCopies.copyList(Collections.<String>emptyList());
    Set<String> copiedSet = AiModelCopies.copySet(Collections.<String>emptySet());

    assertSame(Collections.emptyList(), copiedList);
    assertSame(Collections.emptySet(), copiedSet);
    assertThrows(UnsupportedOperationException.class, () -> copiedList.add("x"));
  }
}
