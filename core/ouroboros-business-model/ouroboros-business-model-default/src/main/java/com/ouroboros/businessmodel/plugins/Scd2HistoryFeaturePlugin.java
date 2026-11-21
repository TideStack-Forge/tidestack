package com.ouroboros.businessmodel.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.businessmodel.BusinessModelTypes;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlugin;
import com.ouroboros.businessmodel.plugin.PluginMetadata;
import com.ouroboros.businessmodel.plugin.PluginProjector;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.schema.PluginEditorSchemaContributor;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelMetaDecorator;

/**
 * SCD2 history tracking feature plugin.
 * <p>
 * Enables Slowly Changing Dimension Type 2 history tracking where changes to records
 * are tracked by creating new versions in a generated parallel history model.
 */
public class Scd2HistoryFeaturePlugin implements BusinessModelFeaturePlugin {
  private static final Logger logger = LoggerFactory.getLogger(Scd2HistoryFeaturePlugin.class);
  private static final String ENABLE_SCD2_HISTORY = "enableScd2History";
  private static final String SCD2_HISTORY_CONFIG = "scd2HistoryConfig";
  private static final String HISTORY_MODEL_FULL_NAME = "historyModelFullName";
  private static final String HISTORY_MODEL_SUFFIX = "History";

  @Override
  public String getPluginId() {
    return "scd2History";
  }

  @Override
  public String getDisplayName() {
    return "SCD2历史追踪";
  }

  @Override
  public PluginEditorSchemaContributor getEditorContributor() {
    return new Scd2HistoryEditorSchemaContributor();
  }

  @Override
  public PluginProjector getProjector() {
    return new Scd2HistoryProjector();
  }

  @Override
  public com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan prepare(BusinessModelMeta meta,
      com.ouroboros.businessmodel.BusinessModel model) {
    boolean enabled = new Scd2HistoryProjector().readEnableScd2History(meta);
    var actions = enabled && model instanceof com.ouroboros.businessmodel.DefaultBusinessModel business
        ? FeatureQueryPlans.actions(business, com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan.Kind.HISTORY) : List.<com.ouroboros.businessmodel.behavior.ActionDefinition>of();
    return new com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan(getPluginId(), actions, context -> {
      if (enabled) { new Scd2HistoryProjector().projectEnabled(meta, context); }
    }, "enableScd2History", enabled
        ? "本模型实际字段变化时按原 SCD2 插件记录历史；忽略字段或无变化不保证新版本，历史副本不另发业务创建事件" : null);
  }

  @Override
  public List<String> getSupportedModelTypes() {
    return Collections.singletonList(BusinessModelTypes.DEFAULT);
  }

  @Override
  public PluginMetadata getMetadata() {
    return new PluginMetadata(
        "1.0.0",
        "SCD2历史追踪功能：通过创建带有有效时间戳的新版本来追踪记录变更",
        "Ouroboros",
        Collections.emptyList()
    );
  }

  private static class Scd2HistoryProjector implements PluginProjector {

    @Override
    public void project(BusinessModelMeta meta, ModelProjectionPipelineContext<?> context) {
      if (!readEnableScd2History(meta)) { return; }
      projectEnabled(meta, context);
    }

    private void projectEnabled(BusinessModelMeta meta, ModelProjectionPipelineContext<?> context) {
      Optional<DataModelMeta> dataModelOptional = context.getModel(DataModelMeta.class, meta.getFullName());
      if (!dataModelOptional.isPresent()) {
        logger.warn("Skip scd2History projection for model {} because target data model is missing",
            meta.getFullName());
        return;
      }
      DataModelMeta sourceMeta = dataModelOptional.get();
      DataModelMeta sourceShape = DataModelMetaDecorator.applyDecorators(sourceMeta);
      List<String> sourcePrimaryKeys = resolvePrimaryKeys(sourceShape);
      FieldNameResolver fieldNames = new FieldNameResolver(sourceShape, sourcePrimaryKeys);
      DataModelMeta historyMeta = buildHistoryModel(meta, sourceMeta, sourceShape, context, sourcePrimaryKeys, fieldNames);
      context.putModel(DataModelMeta.class, historyMeta.getFullName(), historyMeta);

      Map<String, Object> normalizedConfig = new LinkedHashMap<String, Object>();
      normalizedConfig.put(HISTORY_MODEL_FULL_NAME, historyMeta.getFullName());
      normalizedConfig.put("businessKeyField", fieldNames.businessKeyField);
      normalizedConfig.put("validFromField", fieldNames.validFromField);
      normalizedConfig.put("validToField", fieldNames.validToField);
      normalizedConfig.put("isCurrentField", fieldNames.isCurrentField);
      normalizedConfig.put("opField", fieldNames.opField);
      normalizedConfig.put("operatorField", fieldNames.operatorField);

      sourceMeta.setExtraProp(ENABLE_SCD2_HISTORY, true);
      sourceMeta.setExtraProp(SCD2_HISTORY_CONFIG, normalizedConfig);
      if (context.getSourceModel() instanceof com.ouroboros.businessmodel.DefaultBusinessModel business && !business.getFields().isEmpty()) {
        String key = FeatureQueryPlans.sourceKey(sourceMeta);
        var mappings = new LinkedHashMap<String, String>();
        sourceShape.getFields().forEach(field -> mappings.put(field.getName(), field.getName().equals(key) ? fieldNames.businessKeyField : field.getName()));
        FeatureQueryPlans.publish(context, sourceMeta, historyMeta, new com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan(
            com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan.Kind.HISTORY, sourceMeta.getFullName(), historyMeta.getFullName(),
            key, fieldNames.businessKeyField, mappings, Map.of()));
      }
    }

    private Boolean readEnableScd2History(BusinessModelMeta meta) {
      if (meta instanceof DefaultBusinessModelMeta) {
        return Optional.ofNullable(((DefaultBusinessModelMeta) meta).getEnableScd2History()).orElse(false);
      }
      return meta.getExtraProp(Boolean.class, ENABLE_SCD2_HISTORY).orElse(false);
    }

    private DataModelMeta buildHistoryModel(BusinessModelMeta meta, DataModelMeta sourceMeta, DataModelMeta sourceShape,
                                            ModelProjectionPipelineContext<?> context,
                                            List<String> sourcePrimaryKeys,
                                            FieldNameResolver fieldNames) {
      DataModelMeta historyMeta = new DataModelMeta();
      String historyName = resolveParallelModelName(meta, context, HISTORY_MODEL_SUFFIX);
      historyMeta.setName(historyName);
      historyMeta.setNamespace(meta.getNamespace());
      historyMeta.setLabel(Optional.ofNullable(meta.getTitle()).orElse(meta.getName()) + "历史");
      historyMeta.setDescription("Auto-generated SCD2 history model for " + meta.getFullName());
      historyMeta.setMigrationStrategy(sourceMeta.getMigrationStrategy());
      historyMeta.setPluginDescriptors(deepCopyPluginDescriptors(sourceMeta));
      historyMeta.setPrimaryKeys(Collections.singletonList(fieldNames.historyPrimaryKey));
      historyMeta.setPrimaryKeyGenerator("autoIncrement");
      Optional.ofNullable(sourceMeta.getDataStation()).ifPresent(historyMeta::setDataStation);
      Optional.ofNullable(sourceMeta.getRawName())
          .filter(rawName -> !rawName.trim().isEmpty())
          .map(rawName -> rawName + "_history")
          .ifPresent(historyMeta::setRawName);

      List<DataModelFieldMeta> fields = new ArrayList<DataModelFieldMeta>();
      fields.add(primaryKeyField(fieldNames.historyPrimaryKey, "历史记录ID"));
      sourceShape.getFields().stream()
          .filter(field -> !containsIgnoreCase(sourcePrimaryKeys, field.getName()))
          .map(DataModelFieldMeta::deepCopy)
          .forEach(field -> {
            clearSourceFieldConstraints(field);
            fields.add(field);
          });
      fields.add(metadataField(fieldNames.businessKeyField, "业务主键", businessKeyType(sourceShape, sourcePrimaryKeys), false));
      fields.add(metadataField(fieldNames.validFromField, "生效时间", "DateTime", false));
      fields.add(metadataField(fieldNames.validToField, "失效时间", "DateTime", true));
      fields.add(metadataField(fieldNames.isCurrentField, "当前版本", "Boolean", false));
      fields.add(metadataField(fieldNames.opField, "操作类型", "String", false));
      fields.add(metadataField(fieldNames.operatorField, "操作人", "String", true));
      historyMeta.setFields(fields);
      return historyMeta;
    }

    private List<com.ouroboros.data.model.PluginDescriptor> deepCopyPluginDescriptors(DataModelMeta sourceMeta) {
      return sourceMeta.getPluginDescriptors().stream()
          .map(com.ouroboros.data.model.PluginDescriptor::deepCopy)
          .collect(Collectors.toList());
    }

    private List<String> resolvePrimaryKeys(DataModelMeta sourceMeta) {
      if (sourceMeta.getPrimaryKeys() != null && !sourceMeta.getPrimaryKeys().isEmpty()) {
        return sourceMeta.getPrimaryKeys();
      }
      return sourceMeta.getFields().stream()
          .filter(field -> Boolean.TRUE.equals(field.getExtraProp(Boolean.class, "isPrimaryKey").orElse(false)))
          .map(DataModelFieldMeta::getName)
          .collect(Collectors.toList());
    }

    private String businessKeyType(DataModelMeta sourceMeta, List<String> sourcePrimaryKeys) {
      if (sourcePrimaryKeys.isEmpty()) {
        return "String";
      }
      String primaryKey = sourcePrimaryKeys.get(0);
      return sourceMeta.getFields().stream()
          .filter(field -> primaryKey.equals(field.getName()))
          .findFirst()
          .map(DataModelFieldMeta::getType)
          .orElse("String");
    }

    private String resolveParallelModelName(BusinessModelMeta meta, ModelProjectionPipelineContext<?> context, String suffix) {
      String sourceName = Optional.ofNullable(meta.getName()).orElse("model");
      String baseName = sourceName + suffix;
      String candidateName = baseName;
      int index = 1;
      while (context.getModel(DataModelMeta.class, fullName(meta.getNamespace(), candidateName)).isPresent()) {
        candidateName = baseName + index;
        index++;
      }
      return candidateName;
    }

    private String fullName(String namespace, String name) {
      return namespace == null || namespace.trim().isEmpty() ? name : namespace + "." + name;
    }

    private void clearSourceFieldConstraints(DataModelFieldMeta field) {
      field.setIsAutoIncrement(false);
      field.setIsUnique(false);
      if (field.getExtraProps() != null) {
        field.getExtraProps().remove("isPrimaryKey");
        field.getExtraProps().remove("primaryKeyGenerator");
      }
    }

    private DataModelFieldMeta primaryKeyField(String name, String label) {
      DataModelFieldMeta field = metadataField(name, label, "Long", false);
      field.setIsAutoIncrement(true);
      field.setExtraProp("isPrimaryKey", true);
      field.setExtraProp("primaryKeyGenerator", "autoIncrement");
      return field;
    }

    private DataModelFieldMeta metadataField(String name, String label, String type, boolean nullable) {
      DataModelFieldMeta field = new DataModelFieldMeta();
      field.setName(name);
      field.setLabel(label);
      field.setType(type);
      field.setIsNullable(nullable);
      return field;
    }

    private boolean containsIgnoreCase(List<String> names, String candidate) {
      return names.stream().anyMatch(name -> name != null && name.equalsIgnoreCase(candidate));
    }

    private String uniqueName(String preferredName, List<String> reservedNames) {
      String candidate = preferredName;
      int index = 1;
      while (containsIgnoreCase(reservedNames, candidate)) {
        candidate = preferredName + index;
        index++;
      }
      reservedNames.add(candidate);
      return candidate;
    }

    private class FieldNameResolver {
      private final String historyPrimaryKey;
      private final String businessKeyField;
      private final String validFromField;
      private final String validToField;
      private final String isCurrentField;
      private final String opField;
      private final String operatorField;

      private FieldNameResolver(DataModelMeta sourceMeta, List<String> sourcePrimaryKeys) {
        List<String> reservedNames = sourceMeta.getFields().stream()
            .filter(field -> !containsIgnoreCase(sourcePrimaryKeys, field.getName()))
            .map(DataModelFieldMeta::getName)
            .collect(Collectors.toCollection(ArrayList::new));
        this.historyPrimaryKey = containsIgnoreCase(reservedNames, "id")
            ? uniqueName("historyId", reservedNames)
            : uniqueName("id", reservedNames);
        this.businessKeyField = uniqueName("businessKey", reservedNames);
        this.validFromField = uniqueName("validFrom", reservedNames);
        this.validToField = uniqueName("validTo", reservedNames);
        this.isCurrentField = uniqueName("isCurrent", reservedNames);
        this.opField = uniqueName("opType", reservedNames);
        this.operatorField = uniqueName("operator", reservedNames);
      }
    }
  }
}
