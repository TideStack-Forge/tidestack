package com.ouroboros.logicflow.model.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public abstract class AbstractLogicFlowExtraPropsModel<M extends AbstractLogicFlowExtraPropsModel<M, B>, B extends AbstractLogicFlowExtraPropsModel.Builder<M, B>>
    extends AbstractLogicFlowModel<M, B> {
  protected Map<String, Object> extraProps = new LinkedHashMap<>();

  @JsonAnyGetter
  public final Map<String, Object> getExtraProps() {
    return Collections.unmodifiableMap(extraProps);
  }

  public final Optional<Object> getExtraProp(String key) {
    return Optional.ofNullable(extraProps.get(key));
  }

  public final <T> Optional<T> getExtraProp(Class<T> type, String key) {
    return getExtraProp(key).map(value -> type.isInstance(value) ? type.cast(value) : null);
  }

  @JsonAnySetter
  protected final void setExtraProp(String key, Object value) {
    extraProps.put(key, value);
  }

  @Override
  public M clone() {
    var cloned = super.clone();
    cloned.extraProps = new LinkedHashMap<>(extraProps);
    return cloned;
  }

  public abstract static class Builder<M extends AbstractLogicFlowExtraPropsModel<M, ? extends Builder<M, B>>, B extends Builder<M, B>>
      extends AbstractLogicFlowModel.Builder<M> {
    public final B extraProps(Map<String, Object> props) {
      model.extraProps = props == null ? new LinkedHashMap<>() : new LinkedHashMap<>(props);
      return (B) this;
    }

    public final B extraProp(String key, Object value) {
      model.setExtraProp(key, value);
      return (B) this;
    }
  }
}
