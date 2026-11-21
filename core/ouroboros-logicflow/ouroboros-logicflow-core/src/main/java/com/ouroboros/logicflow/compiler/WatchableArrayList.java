package com.ouroboros.logicflow.compiler;

import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

public class WatchableArrayList<T> extends ArrayList<T> {
  final private Function<T, Boolean> addFn;
  private String sessionId;

  public WatchableArrayList(Consumer<WatchableArrayList<T>> initWatcher, Consumer<T> addWatcher) {
    super();

    this.addFn = addWatcher == null
        ? super::add
        : t -> {
      var result = super.add(t);
      addWatcher.accept(t);
      return result;
    };

    if (initWatcher != null) {
      initWatcher.accept(this);
    }
  }

  public WatchableArrayList(int initialCapacity, Consumer<WatchableArrayList<T>> initWatcher, Consumer<T> addWatcher) {
    super(initialCapacity);

    this.addFn = addWatcher == null
        ? super::add
        : t -> {
      var result = super.add(t);
      addWatcher.accept(t);
      return result;
    };

    if (initWatcher != null) {
      initWatcher.accept(this);
    }
  }

  @Override
  public boolean add(T t) {
    return addFn.apply(t);
  }

  public String getSessionId() {
    return sessionId;
  }

  void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
