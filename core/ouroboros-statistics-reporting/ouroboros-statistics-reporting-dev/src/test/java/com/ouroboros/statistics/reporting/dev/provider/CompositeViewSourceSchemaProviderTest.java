package com.ouroboros.statistics.reporting.dev.provider;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ouroboros.businessmodel.validation.CompositeViewSourceSchemaProvider;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.util.SPI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompositeViewSourceSchemaProviderTest {
  @AfterEach
  void clearApp() {
    DevAppHolder.removeAppName();
  }

  @Test
  void selectsWorkspaceBeforeRuntimeAndKeepsMissingWorkspaceSourcesMissing() {
    var dataset = StatisticsReportingDevTestSupport.minimalDataset("demo.orders", true);
    var field = new DatasetFieldDefinition();
    field.setName("orderId");
    field.setDataType("integer");
    field.setNullable(false);
    field.setFilterable(true);
    field.setSortable(false);
    dataset.getSchema().setFields(List.of(field));
    var runtime = mock(DefaultDatasetDefinitionCenter.class);
    when(runtime.getDefinition("demo.orders")).thenReturn(Optional.of(dataset));
    var workspace = mock(DatasetDevService.class);
    when(workspace.getOne("demo.orders")).thenReturn(Optional.empty());
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(SpringContextUtil.class);
      context.getBeanFactory().registerSingleton("datasetDefinitionCenter", runtime);
      context.getBeanFactory().registerSingleton("datasetDevService", workspace);
      context.refresh();
      var providers = SPI.getCachedReversedServiceStream(CompositeViewSourceSchemaProvider.class).toList();
      var selected = providers.stream().filter(CompositeViewSourceSchemaProvider::supportsCurrentContext)
          .findFirst().orElseThrow();
      var schema = selected.resolve("demo.orders").orElseThrow();
      assertTrue(schema.enabled());
      assertEquals("orderId", schema.fields().getFirst().name());
      assertFalse(schema.fields().getFirst().nullable());
      assertTrue(schema.fields().getFirst().filterable());
      assertFalse(schema.fields().getFirst().sortable());
      DevAppHolder.setAppName("demo");
      selected = providers.stream().filter(CompositeViewSourceSchemaProvider::supportsCurrentContext)
          .findFirst().orElseThrow();
      assertInstanceOf(WorkspaceCompositeViewSourceSchemaProvider.class, selected);
      assertTrue(selected.resolve("demo.orders").isEmpty());
      when(workspace.getOne("demo.orders")).thenReturn(Optional.of(dataset));
      dataset.setIsEnabled(false);
      assertFalse(selected.resolve("demo.orders").orElseThrow().enabled());
    }
  }
}
