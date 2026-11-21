package com.ouroboros.businessmodel.projection.uischema;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.Priority;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.uischema.prototypes.CrudUiSchemaModelPrototype;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Converter;


@Priority(30)
public class IndexMPS extends AbstractDefaultBusinessModelProcessStep {

  private final static String UI_SCHEMA_SECTION = "views";
  private final static String UI_SCHEMA_ITEM = "index";

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var customToolbar = context.getAttribute("INDEX_CUSTOM_TOOLBAR").orElseGet(Collections::emptyList);
    var customOperations = context.getAttribute("INDEX_CUSTOM_OPERATIONS").orElseGet(Collections::emptyList);
    var customBulkActions = context.getAttribute("INDEX_CUSTOM_BULK_ACTIONS").orElseGet(Collections::emptyList);
    var customItemActions = context.getAttribute("INDEX_CUSTOM_ITEM_ACTIONS").orElseGet(Collections::emptyList);

    // TODO: 需不需要考虑在某些情况下不生成？

    var indexConfig = model.getExtraPropDeep(Map.class, UI_SCHEMA_SECTION, UI_SCHEMA_ITEM)
        .map(m -> new HashMap<>(m))
        .orElseGet(HashMap::new);
    var isAuto = context.getAttribute(Boolean.class, "hasDataModel", false) && model.getExtraPropDeep(Boolean.class, UI_SCHEMA_SECTION, UI_SCHEMA_ITEM, "isAuto").orElse(true);
    var autoFillHeight = Optional.ofNullable(indexConfig.get("isAutoFillHeight")).map(Converter::toBoolean).orElse(false);
    var isEnabledServerSideExport = model.getExtraPropDeep(Boolean.class, "features", "isEnabledServerSideExport").orElse(false);
    var schemaName = BusinessModelUtil.buildKebabCasePath(model);
    var schemaModel = CrudUiSchemaModelPrototype.get(model)
        .configure(indexConfig)
        .configure("isAuto", isAuto)
        .configure("affixHeader", true)
        .configure("customToolbar", customToolbar)
        .configure("customOperations", customOperations)
        .configure("bulkActions", customBulkActions)
        .configure("itemActions", customItemActions)
        .configure("autoFillHeight", autoFillHeight)
        .configure("workflowEnabled", model.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false))
        .configure("processStartMode", model.getExtraPropDeep(String.class, "workflow", "startMode").orElse("auto"))
        .configure("showProcessButton", model.getExtraPropDeep(Boolean.class, "workflow", "showProcessButton").orElse(true))
        .configure("statusField", model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusField").orElse("__NOT__DEFINED"))
        .configure("submittedStatus", model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusMap", "submitted", "code").orElse(""))
        .configure("statusMap", model.getExtraPropDeep(Map.class, "workflow", "bindings", "0", "statusMap").orElseGet(Collections::emptyMap))
        .configure("importEnabled", model.getExtraPropDeep(Boolean.class, "features", "isEnabledImport").orElse(false))
        .configure("batchDeleteEnabled", model.getExtraPropDeep(Boolean.class, "features", "isEnabledBatchDelete").orElse(false))
        .configure("isEnabledServerSideExport", isEnabledServerSideExport)
        .getModel();
    context.putModel(SchemaNode.class, schemaName, schemaModel);
  }

}
