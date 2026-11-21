package com.ouroboros.security.rbac.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class CollectionCalculateUtilsTest {

  @Test
  void shouldFindNewAndRemovedCollectionByCustomEqualsFunction() {
    var oldCollection = Arrays.asList("a", "b");
    var newCollection = Arrays.asList("b", "c");

    var added = CollectionCalculateUtils.findNewCollection(oldCollection, newCollection, String::equals);
    var removed = CollectionCalculateUtils.findRemoveCollection(oldCollection, newCollection, String::equals);

    assertEquals(Collections.singletonList("c"), added);
    assertEquals(Collections.singletonList("a"), removed);
  }

  @Test
  void shouldFindUpdatedCollectionByPrimaryAndDataComparator() {
    var oldCollection = Arrays.asList(new Item("1", "alpha"), new Item("2", "beta"));
    var newCollection = Arrays.asList(new Item("1", "alpha-updated"), new Item("2", "beta"));

    var updated = CollectionCalculateUtils.findUpdateCollection(
        oldCollection,
        newCollection,
        (left, right) -> left.id.equals(right.id),
        (left, right) -> left.value.equals(right.value)
    );

    assertEquals(1, updated.size());
    assertEquals("1", updated.get(0).id);
    assertEquals("alpha-updated", updated.get(0).value);
  }

  @Test
  void shouldHandleNullCollectionsForDifferenceAndIntersection() {
    var difference = CollectionCalculateUtils.getDifference(null, null, String::equals);
    var intersection = CollectionCalculateUtils.getIntersection(Arrays.asList("x", "y"), null, String::equals);

    assertTrue(difference.isEmpty());
    assertTrue(intersection.isEmpty());
  }

  private static class Item {
    private final String id;
    private final String value;

    private Item(String id, String value) {
      this.id = id;
      this.value = value;
    }
  }
}
