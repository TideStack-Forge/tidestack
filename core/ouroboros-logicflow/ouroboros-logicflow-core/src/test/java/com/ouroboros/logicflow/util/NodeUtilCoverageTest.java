package com.ouroboros.logicflow.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.junit.Test;

public class NodeUtilCoverageTest {
  @Test
  public void buildContextMapShouldHandleEmptyInputsAndIndexedLookups() {
    Map<String, Object> emptyContext = NodeUtil.buildContextMap(Collections.<Object>emptyList(), Collections.<String>emptyList());
    assertTrue(emptyContext.isEmpty());

    List<Object> inputs = Arrays.<Object>asList("first", Integer.valueOf(2));
    List<String> inputNames = Arrays.asList("left", "right");
    Map<String, Object> context = NodeUtil.buildContextMap(inputs, inputNames);

    assertEquals(2, context.size());
    assertTrue(context.containsKey("$0"));
    assertTrue(context.containsKey("$left"));
    assertEquals("first", context.get("$0"));
    assertEquals("first", context.get("$left"));
    assertEquals(Integer.valueOf(2), context.get("$1"));
    assertEquals(Integer.valueOf(2), context.get("$right"));
    assertFalse(context.containsKey("$missing"));
  }

  @Test
  public void buildContextMapShouldWrapMapInputAndExposeEntries() {
    Map<String, Object> payload = new HashMap<String, Object>();
    payload.put("alpha", Integer.valueOf(1));

    Map<String, Object> context = NodeUtil.buildContextMap(Collections.<Object>singletonList(payload), Collections.singletonList("payload"));

    assertEquals(Integer.valueOf(1), context.get("alpha"));
    assertEquals(payload, context.get("$0"));
    assertEquals(payload, context.get("$payload"));
    assertTrue(context.keySet().contains("alpha"));
    assertTrue(context.entrySet().stream().anyMatch(entry -> "alpha".equals(entry.getKey())));
  }

  @Test
  public void buildIsEqualPredicateShouldSupportExpressionsAndDefaultEquality() {
    BiPredicate<Object, Object> defaultPredicate = NodeUtil.buildIsEqualPredicate("");
    assertTrue(defaultPredicate.test("same", "same"));
    assertFalse(defaultPredicate.test("left", "right"));
  }

  @Test
  public void distinctByShouldRejectDuplicatesAccordingToPredicate() {
    Predicate<String> distinct = NodeUtil.distinctBy(new BiPredicate<String, String>() {
      @Override
      public boolean test(String left, String right) {
        return left.equals(right);
      }
    });

    assertTrue(distinct.test("alpha"));
    assertFalse(distinct.test("alpha"));
    assertTrue(distinct.test("beta"));
  }
}
