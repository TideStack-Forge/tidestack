package com.ouroboros.businessmodel.projection.logicflow;

import java.util.Collections;
import java.util.Map;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.util.Maps;

public class ImportMPS extends AbstractDefaultBusinessModelProcessStep {
  private final static String LOGIC_SUFFIX = ".import";

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var isEnabled = model.getExtraPropDeep(Boolean.class, "features", "isEnabledImport").orElse(false);
    if (!isEnabled) {
      return;
    }

    var modelName = model.getFullName();
    var modelTitle = model.getTitle();
    var fullName = model.getFullName() + LOGIC_SUFFIX;
    var dataMappingMode = model.getExtraPropDeep(String.class, "features", "import", "dataMapMode").orElse("auto");
    var dataMapping = model.getExtraPropDeep(Map.class, "features", "import", "dataMap")
        .map(m -> (Map<String, Object>) m)
        .orElseGet(Collections::emptyMap);
    var finalDataMapping = "custom".equalsIgnoreCase(dataMappingMode) ? dataMapping : buildDefaultDataMapping(model);

    var params = Maps.<String, Object>builder()
        .put("dataMapping", finalDataMapping)
        .put("fullName", fullName)
        .put("modelName", modelName)
        .put("modelTitle", modelTitle)
        .build();

    var flowModel = PrototypeTemplate.generate("logic-flow/import", params, FlowModelMeta.class);
    context.putModel(FlowModelMeta.class, fullName, flowModel);
  }

  private Map<String, Object> buildDefaultDataMapping(DefaultBusinessModel model) {
    var mappingBuilder = Maps.<String, Object>builder();
    model.getFields()
        .stream()
        .filter(field -> !field.getIsPrimaryKey())
        .forEach(field -> {
          mappingBuilder.put(field.getName(), "$item.get('" + field.getTitle() + "')");
        });
    model.getRelations()
        .stream()
        .filter(relation -> !"child".equalsIgnoreCase(relation.getType()))
        .forEach(relation -> {
          mappingBuilder.put(relation.getName(), "$item.get('" + relation.getTitle() + "')");
        });
    return mappingBuilder.build();
  }
}
