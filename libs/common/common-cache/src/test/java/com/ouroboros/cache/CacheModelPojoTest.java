package com.ouroboros.cache;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheModelPojoTest {

  @Test
  void clearAllResult_gettersAndSetters_workAsExpected() {
    var failure = new CacheRegionFailure()
        .setName("users")
        .setError("clear failed");

    var result = new ClearAllResult()
        .setTotalRegions(2)
        .setClearedRegions(Collections.singletonList("roles"))
        .setFailedRegions(Collections.singletonList(failure));

    assertEquals(2, result.getTotalRegions());
    assertEquals(Collections.singletonList("roles"), result.getClearedRegions());
    assertEquals(1, result.getFailedRegions().size());
    assertEquals("users", result.getFailedRegions().get(0).getName());
    assertEquals("clear failed", result.getFailedRegions().get(0).getError());
  }

  @Test
  void cacheRegionDescriptor_childrenAndFields_areMutableViaFluentSetters() {
    var parent = new CacheRegionDescriptor()
        .setName("parent")
        .setDisplayName("Parent")
        .setProviderType("ConcurrentMapCache")
        .setInspectable(Boolean.TRUE)
        .setHasCache(Boolean.TRUE)
        .setEntryCount(3L)
        .setEntryCountMode("EXACT");

    var child = new CacheRegionDescriptor().setName("child");
    parent.setChildren(Arrays.asList(child));

    assertEquals("parent", parent.getName());
    assertEquals("Parent", parent.getDisplayName());
    assertEquals("ConcurrentMapCache", parent.getProviderType());
    assertTrue(parent.getInspectable());
    assertTrue(parent.getHasCache());
    assertEquals(Long.valueOf(3L), parent.getEntryCount());
    assertEquals("EXACT", parent.getEntryCountMode());
    assertEquals(1, parent.getChildren().size());
    assertEquals("child", parent.getChildren().get(0).getName());

    parent.setChildren(null);
    assertTrue(parent.getChildren().isEmpty());
  }
}
