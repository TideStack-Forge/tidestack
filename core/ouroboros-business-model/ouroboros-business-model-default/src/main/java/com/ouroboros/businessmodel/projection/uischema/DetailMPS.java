package com.ouroboros.businessmodel.projection.uischema;

import java.util.Collections;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.uischema.prototypes.FormUiSchemaModelPrototype;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.uischema.schema.SchemaNode;

/**
 * 详情页面UI Schema
 */
@Priority(33)
public class DetailMPS extends AbstractDefaultBusinessModelProcessStep {
  private final static String UI_SCHEMA_SECTION = "views";
  private final static String UI_SCHEMA_ITEM = "detail";

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
      return;
    }

    var model = context.getSourceModel();
    var config = model.getExtraPropDeep(Map.class, UI_SCHEMA_SECTION, UI_SCHEMA_ITEM).orElse(Collections.emptyMap());

    var schemaName = BusinessModelUtil.buildKebabCasePath(model) + "/" + UI_SCHEMA_ITEM;
    var schemaModel = FormUiSchemaModelPrototype.get(model)
        .configure(config)
        .removeConfig("mode")
        .configure("static", true)
        .configure("showMode", model.getExtraPropDeep(String.class, UI_SCHEMA_SECTION, UI_SCHEMA_ITEM, "mode").orElse("dialog"))
        .configure("layoutType", model.getExtraPropDeep(String.class, UI_SCHEMA_SECTION, UI_SCHEMA_ITEM, "layoutType").orElse("horizontal"))
        .getModel();

    context.putModel(SchemaNode.class, schemaName, schemaModel);
  }


}
