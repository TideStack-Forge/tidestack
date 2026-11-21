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


@Priority(31)
public class AddMPS extends AbstractDefaultBusinessModelProcessStep {
  private final static String UI_SCHEMA_SECTION = "views";
  private final static String UI_SCHEMA_ITEM = "add";

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    if (!context.getAttribute(Boolean.class, "hasDataModel", false)) {
      return;
    }

    var model = context.getSourceModel();
    var config = model.getExtraPropDeep(Map.class, UI_SCHEMA_SECTION, UI_SCHEMA_ITEM).orElse(Collections.emptyMap());
    var workflowEnabled = model.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false);
    var startMode = model.getExtraPropDeep(String.class, "workflow", "startMode").orElse("auto");
    var statusField = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusField").orElse("__NOT__DEFINED");
    var submittedStatus = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusMap", "submitted", "code").orElse("__SUBMITTED__NOT__DEFINED");
    var draftStatus = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusMap", "draft", "code").orElse("__DRAFT__NOT__DEFINED");

    var schemaName = BusinessModelUtil.buildKebabCasePath(model) + "/" + UI_SCHEMA_ITEM;
    var schemaModel = FormUiSchemaModelPrototype.get(model)
        .configure(config)
        .removeConfig("mode")
        .configure("showMode", model.getExtraPropDeep(String.class, UI_SCHEMA_SECTION, UI_SCHEMA_ITEM, "mode").orElse("dialog"))
        .configure("layoutType", model.getExtraPropDeep(String.class, UI_SCHEMA_SECTION, UI_SCHEMA_ITEM, "layoutType").orElse("horizontal"))
        .configure("initApi", null)
        .configure("workflowEnabled", workflowEnabled)
        .configure("startMode", startMode)
        .configure("statusField", statusField)
        .configure("submittedStatus", submittedStatus)
        .configure("draftStatus", draftStatus)
        .getModel();

    context.putModel(SchemaNode.class, schemaName, schemaModel);
  }

}
