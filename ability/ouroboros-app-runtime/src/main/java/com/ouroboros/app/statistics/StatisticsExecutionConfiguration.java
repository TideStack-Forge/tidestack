package com.ouroboros.app.statistics;

import java.util.List;
import java.util.Map;
import jakarta.annotation.Priority;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.metadata.*;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionPolicy;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionPolicyProvider;
import com.ouroboros.util.Json;

/** Adapts the configuration ability through its authoritative snapshot API. */
@Priority(100)
public class StatisticsExecutionConfiguration implements DatasetExecutionPolicyProvider, ConfigurationMetadataProvider, ConfigurationGroupMetadataProvider {
  @Override
  public boolean supportsCurrentContext() {
    var context = SpringContextUtil.getApplicationContext();
    return context != null && context.getBeanProvider(ConfigurationService.class).getIfAvailable() != null;
  }

  @Override
  public DatasetExecutionPolicy load() {
    return read(SpringContextUtil.getBean(ConfigurationService.class));
  }

  public static DatasetExecutionPolicy read(ConfigurationService configuration) {
    var snapshot = configuration.getSnapshot(DatasetExecutionPolicy.KEY);
    return snapshot.map(value -> DatasetExecutionPolicy.parse(value.value(), value.revision()))
        .orElseGet(DatasetExecutionPolicy::defaults);
  }

  @Override
  public List<ConfigurationMetadata> getAllConfigurationMetadata() {
    var metadata = new ConfigurationMetadata();
    metadata.setKey(DatasetExecutionPolicy.KEY);
    metadata.setLabel("统计分析执行限制");
    metadata.setGroupPath("statisticsReporting");
    metadata.setDescription("查询超时单位为秒，分页和导出限制单位为行，数据集只能进一步收紧。");
    metadata.setScope(ConfigScope.BACKEND);
    metadata.setValueType(ConfigValueType.OBJECT);
    metadata.setModifiable(true);
    metadata.setNullable(false);
    metadata.setEditorSchema(new com.ouroboros.uischema.schema.SchemaNode(Map.of(
        "type", "input-sub-form", "btnLabel", "修改执行限制", "required", true,
        "form", Map.of("title", "统计分析执行限制", "body", List.of(
            limitField("queryTimeoutSeconds", "查询超时（秒）"),
            limitField("maxPageSize", "最大分页条数"),
            limitField("maxExportRows", "最大导出条数"))))));
    var defaults = DatasetExecutionPolicy.defaults();
    metadata.setDefaultValue(Json.toJsonString(Map.of("queryTimeoutSeconds", defaults.queryTimeoutSeconds(),
        "maxPageSize", defaults.maxPageSize(), "maxExportRows", defaults.maxExportRows())));
    return List.of(metadata);
  }
  @Override
  public List<ConfigurationGroupMetadata> getAllConfigurationGroupMetadata() {
    return List.of(new ConfigurationGroupMetadata().setPath("statisticsReporting").setTitle("统计分析")
        .setDescription("查询、分页与导出执行限制"));
  }

  private static Map<String, Object> limitField(String name, String label) {
    return Map.of("type", "input-number", "name", name, "label", label,
        "min", 1, "max", Integer.MAX_VALUE, "precision", 0, "required", true);
  }

}
