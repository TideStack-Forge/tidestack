package com.ouroboros.businessmodel.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.data.model.deletepolicy.ArchiveModelContract;

/**
 * Archive delete feature plugin.
 * <p>
 * Enables archive delete mode where deleted records are copied to a generated
 * parallel archive model before physical deletion from the source table.
 */
public class ArchiveDeleteFeaturePlugin implements BusinessModelFeaturePlugin {
  private static final Logger logger = LoggerFactory.getLogger(ArchiveDeleteFeaturePlugin.class);
  private static final String ENABLE_ARCHIVE_DELETE = "enableArchiveDelete";
  private static final String ARCHIVE_MODEL = "archiveModel";
  private static final String BATCH_SIZE = "batchSize";
  private static final String SOURCE_FIELD_MAPPINGS = "sourceFieldMappings";
  private static final String PLUGIN_ARCHIVE_DELETE = "ArchiveDelete";
  private static final String PLUGIN_LOGICAL_DELETE = "LogicalDelete";
  private static final Integer DEFAULT_BATCH_SIZE = 1000;
  private static final String ARCHIVE_MODEL_SUFFIX = "Archive";
  private static final String ARCHIVE_PRIMARY_KEY = "id";

  @Override
  public String getPluginId() {
    return "archiveDelete";
  }

  @Override
  public String getDisplayName() {
    return "归档删除";
  }

  @Override
  public PluginEditorSchemaContributor getEditorContributor() {
    return new ArchiveDeleteEditorSchemaContributor();
  }

  @Override
  public PluginProjector getProjector() {
    return new ArchiveDeleteProjector();
  }

  @Override
  public com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan prepare(BusinessModelMeta meta,
      com.ouroboros.businessmodel.BusinessModel model) {
    boolean enabled = new ArchiveDeleteProjector().readEnableArchiveDelete(meta);
    var actions = enabled && model instanceof com.ouroboros.businessmodel.DefaultBusinessModel business
        ? FeatureQueryPlans.actions(business, com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan.Kind.ARCHIVE) : List.<com.ouroboros.businessmodel.behavior.ActionDefinition>of();
    return new com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlan(getPluginId(), actions, context -> {
      if (enabled) { new ArchiveDeleteProjector().projectEnabled(meta, context); }
    }, "enableArchiveDelete", enabled
        ? "实际删除本模型对象时先转存归档再物理删除；仍为源模型 DELETE，归档副本不产生本模型 recordsCreated" : null);
  }

  @Override
  public List<String> getSupportedModelTypes() {
    return Collections.singletonList(BusinessModelTypes.DEFAULT);
  }

  @Override
  public PluginMetadata getMetadata() {
    return new PluginMetadata(
        "1.0.0",
        "归档删除功能：将删除的记录复制到归档模型中，然后从源表物理删除",
        "Ouroboros",
        Collections.emptyList()
    );
  }

  private static class ArchiveDeleteProjector implements PluginProjector {

    @Override
    public void project(BusinessModelMeta meta, ModelProjectionPipelineContext<?> context) {
      if (!readEnableArchiveDelete(meta)) { return; }
      projectEnabled(meta, context);
    }

    private void projectEnabled(BusinessModelMeta meta, ModelProjectionPipelineContext<?> context) {
      Optional<DataModelMeta> dataModelOptional = context.getModel(DataModelMeta.class, meta.getFullName());
      if (!dataModelOptional.isPresent()) {
        logger.warn("Skip archiveDelete projection for model {} because target data model is missing",
            meta.getFullName());
        return;
      }
      DataModelMeta sourceMeta = dataModelOptional.get();
      DataModelMeta sourceShape = DataModelMetaDecorator.applyDecorators(sourceMeta);
      Map<String, String> sourceFieldMappings = new LinkedHashMap<String, String>();
      DataModelMeta archiveMeta = buildArchiveModel(meta, sourceMeta, sourceShape, context, sourceFieldMappings);
      context.putModel(DataModelMeta.class, archiveMeta.getFullName(), archiveMeta);

      Map<String, Object> pluginConfig = new LinkedHashMap<String, Object>();
      pluginConfig.put(ARCHIVE_MODEL, archiveMeta.getFullName());
      pluginConfig.put(BATCH_SIZE, DEFAULT_BATCH_SIZE);
      if (!sourceFieldMappings.isEmpty()) {
        pluginConfig.put(SOURCE_FIELD_MAPPINGS, sourceFieldMappings);
      }

      upsertArchiveDeletePlugin(sourceMeta, pluginConfig);
      if (context.getSourceModel() instanceof com.ouroboros.businessmodel.DefaultBusinessModel business && !business.getFields().isEmpty()) {
        var mappings = new LinkedHashMap<String, String>();
        sourceShape.getFields().forEach(field -> mappings.put(field.getName(), sourceFieldMappings.getOrDefault(field.getName(), field.getName())));
        String key = FeatureQueryPlans.sourceKey(sourceMeta);
        FeatureQueryPlans.publish(context, sourceMeta, archiveMeta, new com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan(
            com.ouroboros.businessmodel.action.BusinessActionFeatureQueryPlan.Kind.ARCHIVE, sourceMeta.getFullName(), archiveMeta.getFullName(),
            key, mappings.get(key), mappings, Map.of(ArchiveModelContract.FIELD_SOURCE_MODEL, sourceMeta.getFullName())));
      }
    }

    private Boolean readEnableArchiveDelete(BusinessModelMeta meta) {
      if (meta instanceof DefaultBusinessModelMeta) {
        return Optional.ofNullable(((DefaultBusinessModelMeta) meta).getEnableArchiveDelete()).orElse(false);
      }
      return meta.getExtraProp(Boolean.class, ENABLE_ARCHIVE_DELETE).orElse(false);
    }

    private void upsertArchiveDeletePlugin(DataModelMeta sourceMeta, Map<String, Object> pluginConfig) {
      List<PluginDescriptor> pluginDescriptors = new ArrayList<PluginDescriptor>();
      for (PluginDescriptor pluginDescriptor : sourceMeta.getPluginDescriptors()) {
        String pluginName = pluginDescriptor.getName();
        if (!PLUGIN_LOGICAL_DELETE.equalsIgnoreCase(pluginName)
            && !PLUGIN_ARCHIVE_DELETE.equalsIgnoreCase(pluginName)) {
          pluginDescriptors.add(pluginDescriptor);
        }
      }
      pluginDescriptors.add(new PluginDescriptor(PLUGIN_ARCHIVE_DELETE, pluginConfig));
      sourceMeta.setPluginDescriptors(pluginDescriptors);
    }

    private DataModelMeta buildArchiveModel(BusinessModelMeta meta, DataModelMeta sourceMeta, DataModelMeta sourceShape,
                                            ModelProjectionPipelineContext<?> context,
                                            Map<String, String> sourceFieldMappings) {
      DataModelMeta archiveMeta = new DataModelMeta();
      String archiveName = resolveParallelModelName(meta, context, ARCHIVE_MODEL_SUFFIX);
      archiveMeta.setName(archiveName);
      archiveMeta.setNamespace(meta.getNamespace());
      archiveMeta.setLabel(Optional.ofNullable(meta.getTitle()).orElse(meta.getName()) + "归档");
      archiveMeta.setDescription("Auto-generated archive model for " + meta.getFullName());
      archiveMeta.setMigrationStrategy(sourceMeta.getMigrationStrategy());
      archiveMeta.setPluginDescriptors(deepCopyPluginDescriptors(sourceMeta));
      archiveMeta.setPrimaryKeys(Collections.singletonList(ARCHIVE_PRIMARY_KEY));
      archiveMeta.setPrimaryKeyGenerator("autoIncrement");
      Optional.ofNullable(sourceMeta.getDataStation()).ifPresent(archiveMeta::setDataStation);
      Optional.ofNullable(sourceMeta.getRawName())
          .filter(rawName -> !rawName.trim().isEmpty())
          .map(rawName -> rawName + "_archive")
          .ifPresent(archiveMeta::setRawName);

      List<String> reservedNames = new ArrayList<String>();
      reservedNames.add(ARCHIVE_PRIMARY_KEY);
      reservedNames.addAll(ArchiveModelContract.getRequiredMetadataFields());

      List<DataModelFieldMeta> fields = new ArrayList<DataModelFieldMeta>();
      fields.add(primaryKeyField(ARCHIVE_PRIMARY_KEY, "归档记录ID"));
      for (DataModelFieldMeta sourceField : sourceShape.getFields()) {
        DataModelFieldMeta archiveField = sourceField.deepCopy();
        clearSourceFieldConstraints(archiveField);
        String archiveFieldName = resolveSourceArchiveFieldName(sourceField.getName(), reservedNames);
        if (!archiveFieldName.equals(sourceField.getName())) {
          sourceFieldMappings.put(sourceField.getName(), archiveFieldName);
          archiveField.setName(archiveFieldName);
        }
        reservedNames.add(archiveField.getName());
        fields.add(archiveField);
      }
      fields.add(metadataField(ArchiveModelContract.FIELD_SOURCE_MODEL, "源模型", "String", false));
      fields.add(metadataField(ArchiveModelContract.FIELD_SOURCE_DATA_STATION, "源数据站", "String", true));
      fields.add(metadataField(ArchiveModelContract.FIELD_SOURCE_PRIMARY_KEY, "源主键", "String", false));
      fields.add(metadataField(ArchiveModelContract.FIELD_DELETED_AT, "删除时间", "DateTime", false));
      fields.add(metadataField(ArchiveModelContract.FIELD_DELETED_BY, "删除人", "String", true));
      fields.add(metadataField(ArchiveModelContract.FIELD_DELETE_OPERATION_ID, "删除操作ID", "String", false));
      archiveMeta.setFields(fields);
      return archiveMeta;
    }

    private List<com.ouroboros.data.model.PluginDescriptor> deepCopyPluginDescriptors(DataModelMeta sourceMeta) {
      return sourceMeta.getPluginDescriptors().stream()
          .map(com.ouroboros.data.model.PluginDescriptor::deepCopy)
          .collect(java.util.stream.Collectors.toList());
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

    private String resolveSourceArchiveFieldName(String sourceFieldName, List<String> reservedNames) {
      if (!containsIgnoreCase(reservedNames, sourceFieldName)) {
        return sourceFieldName;
      }
      return uniqueName("source" + upperCaseFirst(sourceFieldName), reservedNames);
    }

    private String uniqueName(String preferredName, List<String> reservedNames) {
      String candidate = preferredName;
      int index = 1;
      while (containsIgnoreCase(reservedNames, candidate)) {
        candidate = preferredName + index;
        index++;
      }
      return candidate;
    }

    private boolean containsIgnoreCase(List<String> names, String candidate) {
      return names.stream().anyMatch(name -> name != null && name.equalsIgnoreCase(candidate));
    }

    private String upperCaseFirst(String value) {
      if (value == null || value.isEmpty()) {
        return "";
      }
      return value.length() == 1 ? value.toUpperCase() : value.substring(0, 1).toUpperCase() + value.substring(1);
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
  }
}
