package com.ouroboros.logicflow.compiler;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;

public class WatchableArrayListCoverageTest {
  @Test
  public void constructorShouldInvokeInitWatcherAndUseDefaultAddImplementation() {
    final List<Integer> initSizes = new ArrayList<Integer>();

    WatchableArrayList<String> list = new WatchableArrayList<String>(new Consumer<WatchableArrayList<String>>() {
      @Override
      public void accept(WatchableArrayList<String> watchableArrayList) {
        initSizes.add(Integer.valueOf(watchableArrayList.size()));
        watchableArrayList.add("seed");
      }
    }, null);

    list.setSessionId("session-1");

    assertEquals(Arrays.asList(Integer.valueOf(0)), initSizes);
    assertEquals(1, list.size());
    assertEquals("seed", list.get(0));
    assertEquals("session-1", list.getSessionId());
  }

  @Test
  public void constructorShouldInvokeAddWatcherWhenPresent() {
    final List<String> addedValues = new ArrayList<String>();

    WatchableArrayList<String> list = new WatchableArrayList<String>(4, null, new Consumer<String>() {
      @Override
      public void accept(String value) {
        addedValues.add(value);
      }
    });

    assertEquals(true, list.add("alpha"));
    assertEquals(true, list.add("beta"));

    assertEquals(Arrays.asList("alpha", "beta"), addedValues);
    assertEquals(Arrays.asList("alpha", "beta"), list);
  }
}
