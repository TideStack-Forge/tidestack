package com.ouroboros.businessmodel.projection.workflow.uischema;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.uischema.prototypes.FormUiSchemaModelPrototype;
import com.ouroboros.businessmodel.projection.workflow.AbstractWorkflowMPS;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.uischema.schema.FormNode;
import com.ouroboros.uischema.schema.SchemaNode;

public class ApprovalFormUISchema extends AbstractWorkflowMPS {
  private static final String PATH_SUFFIX = "/{id}/approval";

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var path = BusinessModelUtil.buildKebabCasePath(model) + PATH_SUFFIX;
    var workflow = model.getExtraPropDeep(Map.class, "workflow").orElseGet(Collections::emptyMap);
    var showMode = model.getExtraPropDeep(String.class, "workflow", "approvalFormShowMode").orElse("dialog");

    var config = new TreeMap<>(String::compareToIgnoreCase);
    var modelPath = BusinessModelUtil.buildKebabCasePath(model) + "/";
    var title = model.getTitle();
    var formSchema = buildFormSchema(model, showMode);
    var formItems = formSchema.getBody();
    if (formItems != null) {
      formItems.forEach(item -> {
        if (item == null) {
          return;
        }
        var name = item.getName();
        if (StringUtils.isBlank(name)) {
          return;
        }
        item.setHiddenOn(String.format("${ GET(taskInfo, 'fields.%s.readable') == false }", name));
        item.setProp("disabledOn", String.format("${ GET(taskInfo, 'fields.%s.writable') == false }", name));
        if (item.getProp("required") == null && item.getProp("requiredOn") == null) {
          item.setProp("requiredOn", String.format("${ GET(taskInfo, 'fields.%s.required') == true }", name));
        }
        item.setProp("staticOn", String.format("${ GET(taskInfo, 'fields.%s') == null }", name));
      });
    }

    config.put("title", title);
    config.put("modelPath", modelPath);
    config.put("formItems", formItems);
    config.put("workflow", workflow);
    config.put("showMode", showMode);

    var schemaModel = PrototypeTemplate.generate("ui-schema/workflow/approvalForm", config, FormNode.class);
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
        .configure("isEdit", true)
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
