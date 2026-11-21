package com.ouroboros.logicflow.util;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.logicflow.expression.LogicFlowExpression;

public final class NodeUtil {
  private NodeUtil() {
  }

  /**
   * 构造表达式计算用的上下文
   *
   * @param inputs     输入
   * @param inputNames 输入端口
   * @return 上下文
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Map<String, Object> buildContextMap(List<Object> inputs, List<String> inputNames) {
    if (inputs == null || inputs.isEmpty()) {
      return Collections.emptyMap();
    }

    var context = new LazyContextMap(inputs, inputNames);
    // 如果第一个输入是 Map，那么将其展开，加入到上下文中
    if (inputs.size() == 1 && inputs.get(0) instanceof Map map) {
      return new ContextWrappingMap(context, map);
    }
    return context;
  }

  public static class LazyContextMap implements Map<String, Object> {
    private final List<Object> inputs;
    private final List<String> inputNames;
    private Set<String> keys;
    private Set<Entry<String, Object>> entries;

    public LazyContextMap(List<Object> inputs, List<String> inputNames) {
      this.inputs = inputs;
      this.inputNames = inputNames;
    }

    @Override
    public int size() {
      return inputs.size();
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean containsKey(Object key) {
      if (!(key instanceof CharSequence)) {
        return false;
      }
      if (!key.toString().startsWith("$")) {
        return false;
      }
      var actualKey = key.toString().substring(1);
      if (actualKey.isEmpty()) {
        return false;
      }
      if (actualKey.matches("\\d+")) {
        var index = Integer.parseInt(actualKey);
        return index < inputs.size();
      }
      return inputNames.contains(actualKey);
    }

    @Override
    public boolean containsValue(Object value) {
      return inputs.contains(value);
    }

    @Override
    public Object get(Object key) {
      if (!(key instanceof CharSequence)) {
        return null;
      }
      if (!key.toString().startsWith("$")) {
        return null;
      }
      var actualKey = key.toString().substring(1);
      if (actualKey.isEmpty()) {
        return null;
      }
      if (actualKey.matches("\\d+")) {
        var index = Integer.parseInt(actualKey);
        if (index >= inputs.size() || index < 0) {
          return null;
        }
        return inputs.get(index);
      }
      var index = inputNames.indexOf(actualKey);
      if (index == -1) {
        return null;
      }
      return inputs.get(index);
    }

    @Override
    public Object put(String key, Object value) {
      return null;
    }

    @Override
    public Object remove(Object key) {
      return null;
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {

    }

    @Override
    public void clear() {

    }

    @Override

    public Set<String> keySet() {
      if (keys == null) {
        keys = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
          keys.add("$" + i);
          if (i < inputNames.size() && ObjectUtils.isNotEmpty(inputNames.get(i))) {
            keys.add("$" + inputNames.get(i));
          }
        }
      }
      return Collections.unmodifiableSet(keys);
    }

    @Override

    public Collection<Object> values() {
      return Collections.unmodifiableList(inputs);
    }

    @Override

    public Set<Entry<String, Object>> entrySet() {
      if (entries == null) {
        entries = new HashSet<>();
        for (int i = 0; i < inputs.size(); i++) {
          entries.add(new AbstractMap.SimpleEntry<>("$" + i, inputs.get(i)));
          if (i < inputNames.size() && ObjectUtils.isNotEmpty(inputNames.get(i))) {
            entries.add(new AbstractMap.SimpleEntry<>("$" + inputNames.get(i), inputs.get(i)));
          }
        }
      }
      return Collections.unmodifiableSet(entries);
    }
  }


  public static BiPredicate<Object, Object> buildIsEqualPredicate(String compareExpression) {
    if (compareExpression.isEmpty()) {
      return Objects::equals;
    }
    var expression = LogicFlowExpression.wrapExpression(compareExpression);
    return (a, b) -> {
      if (Objects.equals(a, b)) {
        return true;
      }
      var context = new HashMap<String, Object>();
      context.put("a", a);
      context.put("b", b);
      context.put("$0", a);
      context.put("$1", b);
      return LogicFlowExpression.eval(expression, context)
          .map(LogicFlowValues::toBoolean)
          .getOrElseThrow(throwable -> new RuntimeException(throwable));
    };
  }


  public static <T> Predicate<T> distinctBy(BiPredicate<T, T> predicate) {
    Collection<T> list = new ConcurrentLinkedDeque<>();
    return t -> {
      if (list.stream().noneMatch(item -> predicate.test(item, t))) {
        list.add(t);
        return true;
      }
      return false;
    };
  }

  private static class ContextWrappingMap implements Map<String, Object> {
    private final Map<String, Object> wrapped;
    private final Map<?, ?> wrapper;

    private ContextWrappingMap(Map<String, Object> wrapped, Map<?, ?> wrapper) {
      this.wrapped = wrapped == null ? Collections.emptyMap() : wrapped;
      this.wrapper = wrapper == null ? Collections.emptyMap() : wrapper;
    }

    @Override
    public int size() {
      return wrapper.size() + wrapped.size();
    }

    @Override
    public boolean isEmpty() {
      return wrapper.isEmpty() && wrapped.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return wrapper.containsKey(key) || wrapped.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
      return wrapper.containsValue(value) || wrapped.containsValue(value);
    }

    @Override
    public Object get(Object key) {
      return wrapper.containsKey(key) ? wrapper.get(key) : wrapped.get(key);
    }

    @Override
    public Object put(String key, Object value) {
      throw new UnsupportedOperationException("上下文不允许被修改");
    }

    @Override
    public Object remove(Object key) {
      throw new UnsupportedOperationException("上下文不允许被修改");
    }

    @Override
    public void putAll(Map<? extends String, ?> map) {
      throw new UnsupportedOperationException("上下文不允许被修改");
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException("上下文不允许被修改");
    }

    @Override
    public Set<String> keySet() {
      var keys = new HashSet<String>();
      wrapper.keySet().stream().map(Object::toString).forEach(keys::add);
      keys.addAll(wrapped.keySet());
      return Collections.unmodifiableSet(keys);
    }

    @Override
    public Collection<Object> values() {
      var values = new ArrayList<Object>();
      values.addAll(wrapper.values());
      values.addAll(wrapped.values());
      return Collections.unmodifiableList(values);
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
      var entries = new HashSet<Entry<String, Object>>();
      wrapper.forEach((key, value) -> entries.add(new AbstractMap.SimpleEntry<>(key.toString(), value)));
      entries.addAll(wrapped.entrySet());
      return Collections.unmodifiableSet(entries);
    }
  }
}
