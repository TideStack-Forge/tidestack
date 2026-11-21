package com.ouroboros.businessmodel.projection.data.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.businessmodel.BusinessModel;
import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.data.model.DataModelFieldMeta;
import com.ouroboros.data.model.DataModelMeta;
import com.ouroboros.data.model.DataModelMetaDecorator;
import com.ouroboros.data.model.PluginDescriptor;
import com.ouroboros.data.model.UniquenessScope;

class PluginFeaturesMPSTest {

  @Test
  void preparedFeatureContributesBehaviorBeforeItsDataProjectionAndCannotPublishAfterFailure() {
    var meta = createMeta(); meta.setNamespace("featurePlanCases");
    var id = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta();
    id.setName("id"); id.setType("String"); id.setIsPrimaryKey(true); meta.setFields(List.of(id));
    var model = new DefaultBusinessModel(meta, emptyProvider());
    assertTrue(model.getBehavior().getActions().containsKey("featureQuery"));
    assertFalse(meta.getExtraProp("featurePlanApplied").isPresent());
    var bundle = model.buildModelBundle();
    assertTrue(bundle.getModel(DataModelMeta.class, meta.getFullName()).orElseThrow()
        .getExtraProp(Boolean.class, "featurePlanApplied").orElse(false));
    meta.setExtraProp("failFeatureProjection", true);
    var failure = org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.exception.BusinessException.class,
        () -> new DefaultBusinessModel(meta, emptyProvider()).buildModelBundle());
    assertTrue(failure.getMessage().contains("featurePlanFixture"));
    assertTrue(bundle.getModel(DataModelMeta.class, meta.getFullName()).orElseThrow()
        .getExtraProp(Boolean.class, "featurePlanApplied").orElse(false));
  }

  @Test
  void resolvedReadPlansFollowTheSameParallelModelAndSnapshotFieldCollisionDecisions() {
    var meta = createMeta(); meta.setEnableArchiveDelete(true); meta.setEnableScd2History(true);
    var id = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta(); id.setName("id"); id.setType("String"); id.setIsPrimaryKey(true);
    var key = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta(); key.setName("businessKey"); key.setType("String");
    var name = new com.ouroboros.businessmodel.meta.BusinessModelFieldMeta(); name.setName("sourceModel"); name.setType("String");
    meta.setFields(List.of(id, key, name));
    var model = new DefaultBusinessModel(meta, emptyProvider());
    var context = new ModelProjectionPipelineContext<>(model);
    new com.ouroboros.businessmodel.projection.data.basic.BasicMPS().process(context);
    new com.ouroboros.businessmodel.projection.data.basic.PrimaryKeyMPS().process(context);
    for (String suffix : List.of("Archive", "History")) {
      var reserved = new DataModelMeta(); reserved.setNamespace("demo"); reserved.setName("customer" + suffix);
      context.putModel(DataModelMeta.class, reserved.getFullName(), reserved);
    }
    new PluginFeaturesMPS().process(context);
    new com.ouroboros.businessmodel.projection.behavior.BehaviorMPS().process(context);
    var behavior = context.getModel(com.ouroboros.businessmodel.behavior.BusinessModelBehavior.class, meta.getFullName()).orElseThrow();
    var archive = behavior.getFeatureQueryPlans().get("searchArchive");
    var history = behavior.getFeatureQueryPlans().get("searchHistory");
    assertEquals("demo.customerArchive1", archive.queryModel());
    assertEquals("sourceId", archive.businessKeyField());
    assertEquals("sourceSourceModel", archive.sourceFieldMappings().get("sourceModel"));
    assertEquals("demo.customerHistory1", history.queryModel()); assertEquals("businessKey1", history.businessKeyField());
    assertTrue(context.getErrors().isEmpty());
    var action = new com.ouroboros.businessmodel.behavior.ActionDefinition(); action.setName("searchHistory"); meta.setActions(List.of(action));
    var conflict = new DefaultBusinessModel(meta, emptyProvider());
    assertTrue(conflict.getBehavior().getDefinitionConflicts().stream().anyMatch(message -> message.contains("actions.searchHistory.duplicate")));
  }

  @Test
  void process_projectsArchiveDeletePluginDescriptor() {
    DefaultBusinessModelMeta meta = createMeta();
    meta.setEnableArchiveDelete(true);

    ProjectionResult result = apply(meta);
    DataModelMeta targetMeta = result.targetMeta;

    PluginDescriptor archiveDelete = findPlugin(targetMeta, "ArchiveDelete").orElseThrow(AssertionError::new);
    assertEquals("demo.customerArchive", archiveDelete.getConfig().get("archiveModel"));
    assertEquals(1000, archiveDelete.getConfig().get("batchSize"));
    assertEquals(Collections.singletonMap("id", "sourceId"), archiveDelete.getConfig().get("sourceFieldMappings"));
    assertFalse(findPlugin(targetMeta, "LogicalDelete").isPresent());
    assertFalse(targetMeta.getExtraProp(Map.class, "deletePolicy").isPresent());
    assertEquals(UniquenessScope.ALL_RECORDS, scopeOf(targetMeta));

    DataModelMeta archiveMeta = result.context.getModel(DataModelMeta.class, "demo.customerArchive")
        .orElseThrow(AssertionError::new);
    assertEquals(UniquenessScope.ALL_RECORDS, scopeOf(archiveMeta));
    assertEquals(Collections.singletonList("id"), archiveMeta.getPrimaryKeys());
    assertTrue(fieldNames(archiveMeta).containsAll(Arrays.asList(
        "id",
        "sourceId",
        "name",
        "sourceModel",
        "sourceDataStation",
        "sourcePrimaryKey",
        "deletedAt",
        "deletedBy",
        "deleteOperationId"
    )));
    assertTrue(findField(archiveMeta, "id").getExtraProp(Boolean.class, "isPrimaryKey").orElse(false));
    assertTrue(findField(archiveMeta, "id").getIsAutoIncrement());
  }

  @Test
  void process_projectsSoftDeletePluginDescriptor() {
    DefaultBusinessModelMeta meta = createMeta();
    meta.setExtraProp("features", mapOf("isEnabledSoftDelete", true));

    DataModelMeta targetMeta = applyWithSoftDelete(meta);

    assertTrue(targetMeta.getExtraProp(Boolean.class, "enableSoftDelete").orElse(false));
    assertTrue(targetMeta.getExtraProp(Map.class, "softDeleteConfig").isPresent());
    assertFalse(findPlugin(targetMeta, "LogicalDelete").isPresent());
    assertFalse(findPlugin(targetMeta, "ArchiveDelete").isPresent());
    assertFalse(targetMeta.getExtraProp(Map.class, "deletePolicy").isPresent());
    assertEquals(UniquenessScope.ALL_RECORDS, scopeOf(targetMeta));

    DataModelMeta decoratedMeta = DataModelMetaDecorator.applyDecorators(targetMeta);
    assertTrue(findPlugin(decoratedMeta, "LogicalDelete").isPresent());
    assertEquals(UniquenessScope.ACTIVE_RECORDS, scopeOf(decoratedMeta));
  }

  @Test
  void process_prefersArchiveDeleteWhenSoftDeleteAlsoEnabled() {
    DefaultBusinessModelMeta meta = createMeta();
    meta.setExtraProp("features", mapOf("isEnabledSoftDelete", true));
    meta.setEnableArchiveDelete(true);

    DataModelMeta targetMeta = applyWithSoftDelete(meta);

    assertFalse(targetMeta.getExtraProp(Boolean.class, "enableSoftDelete").orElse(false));
    assertFalse(targetMeta.getExtraProp(Map.class, "softDeleteConfig").isPresent());
    assertTrue(findPlugin(targetMeta, "ArchiveDelete").isPresent());
    assertFalse(findPlugin(targetMeta, "LogicalDelete").isPresent());
    assertFalse(targetMeta.getExtraProp(Map.class, "deletePolicy").isPresent());
    assertEquals(UniquenessScope.ALL_RECORDS, scopeOf(targetMeta));
  }

  @Test
  void process_projectsScd2HistoryExtraProps() {
    DefaultBusinessModelMeta meta = createMeta();
    meta.setEnableScd2History(true);

    ProjectionResult result = apply(meta);
    DataModelMeta targetMeta = result.targetMeta;

    assertTrue(targetMeta.getExtraProp(Boolean.class, "enableScd2History").orElse(false));
    Map<?, ?> scd2Config = targetMeta.getExtraProp(Map.class, "scd2HistoryConfig").orElse(Collections.emptyMap());
    assertEquals("demo.customerHistory", scd2Config.get("historyModelFullName"));
    assertEquals("businessKey", scd2Config.get("businessKeyField"));
    assertEquals("operator", scd2Config.get("operatorField"));
    assertFalse(scd2Config.containsKey("storeDiff"));
    assertFalse(scd2Config.containsKey("ignoreFields"));
    assertFalse(scd2Config.containsKey("maxRows"));

    DataModelMeta historyMeta = result.context.getModel(DataModelMeta.class, "demo.customerHistory")
        .orElseThrow(AssertionError::new);
    assertEquals(Collections.singletonList("id"), historyMeta.getPrimaryKeys());
    assertTrue(fieldNames(historyMeta).containsAll(Arrays.asList(
        "id",
        "name",
        "businessKey",
        "validFrom",
        "validTo",
        "isCurrent",
        "opType",
        "operator"
    )));
    assertFalse(fieldNames(historyMeta).contains("sourceId"));
    assertEquals("Long", findField(historyMeta, "businessKey").getType());
    assertTrue(findField(historyMeta, "id").getExtraProp(Boolean.class, "isPrimaryKey").orElse(false));
    assertTrue(findField(historyMeta, "id").getIsAutoIncrement());
  }

  @Test
  void process_skipsDefaultOnlyPluginsForNonDefaultModelType() {
    DefaultBusinessModelMeta meta = createMeta();
    meta.setModelType("composite-view");
    meta.setEnableArchiveDelete(true);
    meta.setEnableScd2History(true);

    ProjectionResult result = apply(meta);
    DataModelMeta targetMeta = result.targetMeta;

    assertFalse(targetMeta.getExtraProp(Map.class, "deletePolicy").isPresent());
    assertFalse(targetMeta.getExtraProp(Map.class, "scd2HistoryConfig").isPresent());
    assertFalse(result.context.getModel(DataModelMeta.class, "demo.customerArchive").isPresent());
    assertFalse(result.context.getModel(DataModelMeta.class, "demo.customerHistory").isPresent());
    assertFalse(findPlugin(targetMeta, "ArchiveDelete").isPresent());
  }

  private ProjectionResult apply(DefaultBusinessModelMeta meta) {
    DefaultBusinessModel model = new DefaultBusinessModel(meta, emptyProvider());
    ModelProjectionPipelineContext<DefaultBusinessModel> context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    DataModelMeta targetMeta = new DataModelMeta();
    targetMeta.setName(meta.getName());
    targetMeta.setNamespace(meta.getNamespace());
    targetMeta.setFields(Arrays.asList(
        field("id", "Long", true),
        field("name", "String", false)
    ));
    targetMeta.setPluginDescriptors(Collections.singletonList(new PluginDescriptor("NormalizeWhere")));
    context.putModel(DataModelMeta.class, meta.getFullName(), targetMeta);

    new PluginFeaturesMPS().process(context);

    return new ProjectionResult(
        context,
        context.getModel(DataModelMeta.class, meta.getFullName()).orElseThrow(AssertionError::new)
    );
  }

  private DataModelMeta applyWithSoftDelete(DefaultBusinessModelMeta meta) {
    DefaultBusinessModel model = new DefaultBusinessModel(meta, emptyProvider());
    ModelProjectionPipelineContext<DefaultBusinessModel> context = new ModelProjectionPipelineContext<DefaultBusinessModel>(model);
    DataModelMeta targetMeta = new DataModelMeta();
    targetMeta.setName(meta.getName());
    targetMeta.setNamespace(meta.getNamespace());
    context.putModel(DataModelMeta.class, meta.getFullName(), targetMeta);

    new com.ouroboros.businessmodel.projection.data.feature.SoftDeleteMPS().process(context);
    new PluginFeaturesMPS().process(context);

    return context.getModel(DataModelMeta.class, meta.getFullName()).orElseThrow(AssertionError::new);
  }

  private Optional<PluginDescriptor> findPlugin(DataModelMeta meta, String pluginName) {
    List<PluginDescriptor> plugins = meta.getPluginDescriptors();
    return plugins.stream()
        .filter(plugin -> pluginName.equals(plugin.getName()))
        .findFirst();
  }

  private UniquenessScope scopeOf(DataModelMeta meta) {
    return UniquenessScope.fromExtraProp(meta.getExtraProp(UniquenessScope.EXTRA_PROP_NAME).orElse(null));
  }

  private Map<String, Object> mapOf(String key, Object value) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put(key, value);
    return map;
  }

  private DefaultBusinessModelMeta createMeta() {
    DefaultBusinessModelMeta meta = new DefaultBusinessModelMeta();
    meta.setName("customer");
    meta.setNamespace("demo");
    meta.setTitle("客户");
    meta.setFields(Collections.emptyList());
    meta.setRelations(Collections.emptyList());
    return meta;
  }

  private BusinessModelProvider emptyProvider() {
    return new BusinessModelProvider() {
      @Override
      public Optional<BusinessModel> getBusinessModel(String fullName) {
        return Optional.empty();
      }

      @Override
      public Map<String, BusinessModel> getBusinessModelMap() {
        return Collections.emptyMap();
      }
    };
  }

  private DataModelFieldMeta field(String name, String type, boolean primaryKey) {
    DataModelFieldMeta field = new DataModelFieldMeta();
    field.setName(name);
    field.setType(type);
    field.setIsNullable(!primaryKey);
    field.setIsAutoIncrement(primaryKey);
    if (primaryKey) {
      field.setExtraProp("isPrimaryKey", true);
      field.setExtraProp("primaryKeyGenerator", "autoIncrement");
    }
    return field;
  }

  private List<String> fieldNames(DataModelMeta meta) {
    return meta.getFields().stream()
        .map(DataModelFieldMeta::getName)
        .collect(java.util.stream.Collectors.toList());
  }

  private DataModelFieldMeta findField(DataModelMeta meta, String fieldName) {
    return meta.getFields().stream()
        .filter(field -> fieldName.equals(field.getName()))
        .findFirst()
        .orElseThrow(AssertionError::new);
  }

  private static class ProjectionResult {
    private final ModelProjectionPipelineContext<DefaultBusinessModel> context;
    private final DataModelMeta targetMeta;

    private ProjectionResult(ModelProjectionPipelineContext<DefaultBusinessModel> context, DataModelMeta targetMeta) {
      this.context = context;
      this.targetMeta = targetMeta;
    }
  }
}
