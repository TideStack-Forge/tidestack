package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.LinkedHashMap;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.definition.parameter.ParameterDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultDatasetParameterNormalizerTest {

  @Test
  void shouldDropOptionalConditionWhenParameterMissing() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");

    var keyword = new ParameterDefinition();
    keyword.setName("keyword");
    keyword.setRequired(true);

    var region = new ParameterDefinition();
    region.setName("region");
    region.setAllowEmptyOmitCondition(true);

    dataset.setParameters(java.util.Arrays.asList(keyword, region));

    var request = new DatasetExecutionRequest();
    request.setDatasetName("demo.sales");
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("keyword", "phone"));

    var result = new DefaultDatasetParameterNormalizer().normalize(dataset, request);

    assertEquals("phone", result.getAppliedParameters().get("keyword"));
    assertNull(result.getAppliedParameters().get("region"));
  }

  @Test
  void shouldBindSystemContextValueWhenRuntimeParameterMissing() {
    var dataset = new DatasetDefinition();
    dataset.setName("demo.sales");

    var tenantId = new ParameterDefinition();
    tenantId.setName("tenantId");
    tenantId.setSystemContextBinding("tenantId");

    dataset.setParameters(Collections.singletonList(tenantId));

    var request = new DatasetExecutionRequest();
    request.setDatasetName("demo.sales");
    request.setRuntimeParameters(Collections.<String, Object>emptyMap());
    request.setExecutionContext(new LinkedHashMap<String, Object>());
    request.getExecutionContext().put("tenantId", "tenant-a");

    var result = new DefaultDatasetParameterNormalizer().normalize(dataset, request);

    assertEquals("tenant-a", result.getAppliedParameters().get("tenantId"));
  }

  @Test
  void shouldPreferSystemContextValueWhenClientAttemptsToOverrideIt() {
    var dataset = new DatasetDefinition();
    var tenantId = new ParameterDefinition();
    tenantId.setName("tenantId");
    tenantId.setValueSource("systemContext");
    tenantId.setSystemContextBinding("tenantId");
    dataset.setParameters(Collections.singletonList(tenantId));

    var request = new DatasetExecutionRequest();
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("tenantId", "client-tenant"));
    request.setExecutionContext(new LinkedHashMap<String, Object>());
    request.getExecutionContext().put("tenantId", "trusted-tenant");

    var result = new DefaultDatasetParameterNormalizer().normalize(dataset, request);

    assertEquals("trusted-tenant", result.getAppliedParameters().get("tenantId"));
  }

  @Test
  void shouldRejectMissingRequiredSystemContextValueInsteadOfUsingClientValue() {
    var dataset = new DatasetDefinition();
    var tenantId = new ParameterDefinition();
    tenantId.setName("tenantId");
    tenantId.setRequired(true);
    tenantId.setValueSource("systemContext");
    tenantId.setSystemContextBinding("tenantId");
    dataset.setParameters(Collections.singletonList(tenantId));

    var request = new DatasetExecutionRequest();
    request.setRuntimeParameters(Collections.<String, Object>singletonMap("tenantId", "client-tenant"));
    request.setExecutionContext(new LinkedHashMap<String, Object>());

    var error = assertThrows(IllegalArgumentException.class,
        () -> new DefaultDatasetParameterNormalizer().normalize(dataset, request));

    assertEquals("missing required system context parameter: tenantId", error.getMessage());
  }
}
