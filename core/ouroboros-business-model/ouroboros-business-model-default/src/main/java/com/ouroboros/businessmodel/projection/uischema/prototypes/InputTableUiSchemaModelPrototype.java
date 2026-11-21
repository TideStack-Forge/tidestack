package com.ouroboros.businessmodel.projection.uischema.prototypes;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelPrototype;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Converter;

public class InputTableUiSchemaModelPrototype extends AbstractDefaultBusinessModelPrototype<SchemaNode, InputTableUiSchemaModelPrototype> {
  SchemaNode model;

  public InputTableUiSchemaModelPrototype(DefaultBusinessModel businessModel) {
    super(businessModel);
    model = new SchemaNode();
    model.setType("input-table");
    model.setProp("label", false);
    model.setProp("addable", true);
    model.setProp("editable", true);
    model.setProp("removable", true);
    model.setProp("copyable", false);
  }

  public static InputTableUiSchemaModelPrototype get(DefaultBusinessModel businessModel) {
    return new InputTableUiSchemaModelPrototype(businessModel);
  }

  @Override
  public InputTableUiSchemaModelPrototype configure(Map<String, Object> config) {
    this.config.putAll(config);
    return this;
  }

  private void configColumns() {
    var form = FormUiSchemaModelPrototype.get(businessModel)
        .configure("isAuto", true)
        .configure("fields", config.get("fields"))
        .configure("isEdit", config.get("isEdit"))
        .getModel();
    var body = form.getBody();
    var columns = new ArrayList<>();
    var referenceKey = Optional.ofNullable(config.get("referenceKey")).map(Converter::toString).orElse("");
    if (body != null) {
      body.forEach(item -> {
        if (item == null) {
          return;
        }
        // 剔除主表的关联字段
        if (referenceKey.equals(item.getName())) {
          return;
        }
        // 剔除分割线
        if ("divider".equals(item.getType())) {
          return;
        }
        // 删除占位符
        if ("static-text".equals(item.getType()) && StringUtils.isBlank(item.getName())) {
          return;
        }

        // 展开子表
        if ("fieldSet".equals(item.getType())) {
          var label = item.getProp("title");
          var firstBody = Optional.ofNullable(item.getProp(Collection.class, "body", null))
              .orElseGet(Collections::emptyList)
              .stream()
              .findFirst()
              .orElse(null);
          if (firstBody == null) {
            return;
          }
          if (firstBody instanceof SchemaNode schemaNode) {
            schemaNode.setProp("label", label);
            columns.add(schemaNode);
          } else if (firstBody instanceof Map mapNode) {
            mapNode.put("label", label);
            columns.add(new SchemaNode(mapNode));
          }
          return;
        }

        item.setProp("quickEdit", true);

        columns.add(item);
      });
    }
    model.setProp("columns", columns);
  }

  @Override
  public SchemaNode getModel() {
    configColumns();
    model.setName(Optional.ofNullable(config.get("name")).map(Converter::toString).orElse(""));
    return model;
  }
}
