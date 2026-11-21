package com.ouroboros.logicflow.model.support;

import java.util.function.Consumer;

public abstract class AbstractLogicFlowModel<M extends AbstractLogicFlowModel<M, B>, B extends AbstractLogicFlowModel.Builder<M>> implements Cloneable {
  @Override
  public M clone() {
    try {
      return (M) super.clone();
    } catch (CloneNotSupportedException exception) {
      throw new IllegalStateException("模型克隆失败", exception);
    }
  }

  protected abstract B createBuilder();

  @SafeVarargs
  public final M map(Consumer<B>... mappers) {
    var cloned = clone();
    var builder = createBuilder();
    builder.setModel(cloned);
    for (var mapper : mappers) {
      mapper.accept(builder);
    }
    return builder.build();
  }

  public abstract static class Builder<M> {
    protected M model;

    protected Builder() {
    }

    protected void setModel(M model) {
      this.model = model;
    }

    public M build() {
      return model;
    }
  }
}
