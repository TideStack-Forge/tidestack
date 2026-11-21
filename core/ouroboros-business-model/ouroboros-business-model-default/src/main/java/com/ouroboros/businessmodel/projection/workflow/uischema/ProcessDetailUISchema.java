package com.ouroboros.businessmodel.projection.workflow.uischema;

import java.util.Collections;
import java.util.TreeMap;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.uischema.prototypes.FormUiSchemaModelPrototype;
import com.ouroboros.businessmodel.projection.workflow.AbstractWorkflowMPS;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.uischema.schema.FormNode;
import com.ouroboros.uischema.schema.SchemaNode;

public class ProcessDetailUISchema extends AbstractWorkflowMPS {
  private static final String PATH_SUFFIX = "/{id}/process-detail";

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var showMode = model.getExtraPropDeep(String.class, "workflow", "detailShowMode").orElse("dialog");
    var path = BusinessModelUtil.buildKebabCasePath(model) + PATH_SUFFIX;
    var config = new TreeMap<>(String::compareToIgnoreCase);
    var modelPath = BusinessModelUtil.buildKebabCasePath(model) + "/";
    var title = model.getTitle();
    var formSchema = buildFormSchema(model, showMode);
    var actions = formSchema.getActions();
    formSchema.setActions(Collections.emptyList());
    formSchema.setWrapWithPanel(false);

    config.put("title", title);
    config.put("modelPath", modelPath);
    config.put("formSchema", formSchema);
    config.put("showMode", showMode);
    config.put("actions", actions);

    var schemaModel = PrototypeTemplate.generate("ui-schema/workflow/processDetail", config, FormNode.class);


    context.putModel(SchemaNode.class, path, schemaModel);
  }

  private FormNode buildFormSchema(DefaultBusinessModel model, String showMode) {
    var config = new TreeMap<>(String::compareToIgnoreCase);
    var workflowEnabled = model.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false);
    var startMode = model.getExtraPropDeep(String.class, "workflow", "startMode").orElse("auto");
    var statusField = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusField").orElse("__NOT__DEFINED");
    var submittedStatus = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusMap", "submitted", "code").orElse("__SUBMITTED__NOT__DEFINED");
    var draftStatus = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusMap", "draft", "code").orElse("__DRAFT__NOT__DEFINED");

    var schemaModel = FormUiSchemaModelPrototype.get(model)
        .configure(config)
        .removeConfig("mode")
        .configure("showMode", showMode)
        .configure("static", true)
        .configure("layoutType", "horizontal")
        .configure("workflowEnabled", workflowEnabled)
        .configure("startMode", startMode)
        .configure("statusField", statusField)
        .configure("submittedStatus", submittedStatus)
        .configure("draftStatus", draftStatus)
        .getModel();
    return schemaModel;
  }
}
