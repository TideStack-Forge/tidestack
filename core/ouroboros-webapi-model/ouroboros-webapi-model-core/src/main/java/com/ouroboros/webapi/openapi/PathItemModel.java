package com.ouroboros.webapi.openapi;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.1 Path Item 模型
 */
public class PathItemModel extends AbstractExtraPropsModel<PathItemModel, PathItemModel.Builder> implements Model<PathItemModel> {
  private OperationModel get;
  private OperationModel put;
  private OperationModel post;
  private OperationModel delete;
  private OperationModel options;
  private OperationModel head;
  private OperationModel patch;
  private OperationModel trace;

  public static Builder builder() {
    return new Builder();
  }

  public OperationModel getGet() {
    return get;
  }

  public OperationModel getPut() {
    return put;
  }

  public OperationModel getPost() {
    return post;
  }

  public OperationModel getDelete() {
    return delete;
  }

  public OperationModel getOptions() {
    return options;
  }

  public OperationModel getHead() {
    return head;
  }

  public OperationModel getPatch() {
    return patch;
  }

  public OperationModel getTrace() {
    return trace;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<PathItemModel, Builder> {
    public Builder() {
      model = new PathItemModel();
    }

    public Builder get(OperationModel get) {
      model.get = get;
      return this;
    }

    public Builder put(OperationModel put) {
      model.put = put;
      return this;
    }

    public Builder post(OperationModel post) {
      model.post = post;
      return this;
    }

    public Builder delete(OperationModel delete) {
      model.delete = delete;
      return this;
    }

    public Builder options(OperationModel options) {
      model.options = options;
      return this;
    }

    public Builder head(OperationModel head) {
      model.head = head;
      return this;
    }

    public Builder patch(OperationModel patch) {
      model.patch = patch;
      return this;
    }

    public Builder trace(OperationModel trace) {
      model.trace = trace;
      return this;
    }
  }
}
