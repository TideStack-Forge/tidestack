package com.ouroboros.webapi.openapi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.1 Components 模型
 */
public class ComponentsModel extends AbstractExtraPropsModel<ComponentsModel, ComponentsModel.Builder> implements Model<ComponentsModel> {
  private Map<String, SchemaModel> schemas;

  public static Builder builder() {
    return new Builder();
  }

  public Map<String, SchemaModel> getSchemas() {
    return schemas != null ? schemas : Collections.emptyMap();
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<ComponentsModel, Builder> {
    public Builder() {
      model = new ComponentsModel();
    }

    public Builder schemas(Map<String, SchemaModel> schemas) {
      model.schemas = schemas;
      return this;
    }

    public Builder schema(String name, SchemaModel schema) {
      if (model.schemas == null) {
        model.schemas = new LinkedHashMap<>();
      }
      model.schemas.put(name, schema);
      return this;
    }
  }
}
