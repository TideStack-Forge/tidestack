package com.ouroboros.businessmodel.action;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.ouroboros.exception.BusinessException;

/** Invocation-local, cached business values. HTTP inputs extend this class directly. */
public class PreparedBusinessActionInput extends AbstractMap<String, Object>
    implements BusinessActionInput<Object, Object> {
  private final Supplier<Map<String, Object>> reader;
  private Map<String, Object> values;
  private Map<String, Object> preparedSchema;
  private RuntimeException readFailure;

  protected PreparedBusinessActionInput(Supplier<Map<String, Object>> reader) {
    this.reader = reader;
  }

  public static PreparedBusinessActionInput prepare(BusinessActionInput<?, ?> input, Map<String, Object> schema) {
    return prepare(input, new BusinessActionInputPlan(schema));
  }

  static PreparedBusinessActionInput prepare(BusinessActionInput<?, ?> input, BusinessActionInputPlan plan) {
    PreparedBusinessActionInput prepared = input instanceof PreparedBusinessActionInput cached
        ? cached : new PreparedBusinessActionInput(() -> businessValues(input));
    prepared.prepare(plan);
    return prepared;
  }

  private static Map<String, Object> businessValues(BusinessActionInput<?, ?> input) {
    return new AbstractMap<>() {
      @Override public boolean containsKey(Object key) {
        return "params".equals(key) ? input.hasParams() : "data".equals(key) && input.hasData();
      }
      @Override public Object get(Object key) {
        if (!containsKey(key)) { return null; }
        return "params".equals(key) ? input.params() : input.data();
      }
      @Override public Set<String> keySet() {
        Set<String> keys = new java.util.LinkedHashSet<>();
        if (input.hasParams()) { keys.add("params"); }
        if (input.hasData()) { keys.add("data"); }
        return Collections.unmodifiableSet(keys);
      }
      @Override public Set<Entry<String, Object>> entrySet() { return entries(this); }
    };
  }

  private void prepare(BusinessActionInputPlan plan) {
    Map<String, Object> schema = plan.schema();
    if (preparedSchema != null) {
      if (!preparedSchema.equals(schema)) {
        throw new BusinessException("Action 输入不能跨不同契约复用，请创建新输入");
      }
      return;
    }
    Map<String, Object> normalized = BusinessActionInputValues.prepare(schema, read());
    plan.validate(normalized);
    values = normalized;
    preparedSchema = schema;
  }

  public Map<String, Object> schema() {
    if (preparedSchema == null) {
      throw new IllegalStateException("Action input must be prepared before binding");
    }
    return preparedSchema;
  }

  private Map<String, Object> read() {
    if (readFailure != null) {
      throw readFailure;
    }
    if (values == null) {
      try {
        values = Collections.unmodifiableMap(reader.get());
      } catch (RuntimeException failure) {
        readFailure = failure;
        throw failure;
      }
    }
    return values;
  }

  @Override
  public boolean hasParams() {
    return containsKey("params");
  }

  @Override
  public Object params() {
    return get("params");
  }

  @Override
  public boolean hasData() {
    return containsKey("data");
  }

  @Override
  public Object data() {
    return get("data");
  }

  @Override
  public Set<Entry<String, Object>> entrySet() {
    return entries(this);
  }

  @Override
  public Set<String> keySet() {
    return read().keySet();
  }

  @Override
  public Object get(Object key) {
    return read().get(key);
  }

  @Override
  public boolean containsKey(Object key) {
    return read().containsKey(key);
  }

  static Set<Entry<String, Object>> entries(Map<String, Object> map) {
    return new java.util.AbstractSet<>() {
      @Override public int size() { return map.keySet().size(); }
      @Override public java.util.Iterator<Entry<String, Object>> iterator() {
        var keys = map.keySet().iterator();
        return new java.util.Iterator<>() {
          @Override public boolean hasNext() { return keys.hasNext(); }
          @Override public Entry<String, Object> next() {
            String key = keys.next();
            return new Entry<>() {
              @Override public String getKey() { return key; }
              @Override public Object getValue() { return map.get(key); }
              @Override public Object setValue(Object value) { throw new UnsupportedOperationException(); }
              @Override public boolean equals(Object other) {
                return other instanceof Entry<?, ?> entry && key.equals(entry.getKey())
                    && java.util.Objects.equals(getValue(), entry.getValue());
              }
              @Override public int hashCode() { return key.hashCode() ^ java.util.Objects.hashCode(getValue()); }
            };
          }
        };
      }
    };
  }

}
