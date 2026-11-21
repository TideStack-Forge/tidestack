package com.ouroboros.statistics.reporting.dev.controller;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.statistics.reporting.definition.schema.DatasetFieldDefinition;
import com.ouroboros.statistics.reporting.dev.model.LowcodeDatasetDefinition;
import com.ouroboros.statistics.reporting.dev.service.DatasetReferenceResolver;
import com.ouroboros.statistics.reporting.dev.service.impl.DatasetDevService;
import com.ouroboros.statistics.reporting.dev.support.StatisticsReportingDevTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChartFieldOptionsControllerTest {
  private MockMvc mockMvc;
  private ChartFieldOptionsController controller;

  @BeforeEach
  void setUp() {
    DatasetReferenceResolver resolver = (datasetName, enabledOnly) -> Collections.emptyList();

    DatasetDevService datasetDevService = new DatasetDevService(resolver) {
      @Override
      public Optional<LowcodeDatasetDefinition> getOne(String fullName) {
        if (!"demo.sales".equals(fullName)) {
          return Optional.empty();
        }
        LowcodeDatasetDefinition dataset = StatisticsReportingDevTestSupport.minimalDataset(
            "demo.sales",
            false
        );
        field(dataset, "id").setSemanticRole("identifier");
        field(dataset, "region").setSemanticRole("dimension");
        field(dataset, "total").setSemanticRole("metric");
        field(dataset, "total").setDataType("number");
        field(dataset, "name").setSemanticRole("detail");
        field(dataset, "name").setDataType("datetime");
        return Optional.of(dataset);
      }
    };

    controller = new ChartFieldOptionsController(datasetDevService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
  }

  @Test
  void optionsEndpoint_shouldReturnMetricOptionsForMetricParameter() throws Exception {
    mockMvc.perform(get("/api/dev/demo-app/chart/field-options/demo.sales")
            .param("chartType", "bar")
            .param("param", "yFields"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[0].value").value("total"))
        .andExpect(jsonPath("$.data.items[0].semanticRole").value("metric"))
        .andExpect(jsonPath("$.data.items[0].dataType").value("number"));
  }

  @Test
  void optionsEndpoint_shouldReturnCategoryOptionsForCartesianParameter() throws Exception {
    mockMvc.perform(get("/api/dev/demo-app/chart/field-options/demo.sales")
            .param("chartType", "bar")
            .param("param", "xField"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items[0].value").value("id"))
        .andExpect(jsonPath("$.data.items[1].value").value("name"))
        .andExpect(jsonPath("$.data.items[2].value").value("region"))
        .andExpect(jsonPath("$.data.items[0].semanticRole").value("identifier"));
  }

  @Test
  void optionsEndpoint_shouldReturnAllSchemaFieldsForSortSelection() throws Exception {
    mockMvc.perform(get("/api/dev/demo-app/chart/field-options/demo.sales")
            .param("chartType", "bar")
            .param("param", "sortField"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items.length()").value(4))
        .andExpect(jsonPath("$.data.items[3].value").value("total"));
  }

  @Test
  void optionsEndpoint_shouldRejectUnsupportedParameterForChartType() throws Exception {
    PlatformException exception = assertThrows(PlatformException.class,
        () -> controller.options("demo-app", "demo.sales", "pie", "xField"));
    org.junit.jupiter.api.Assertions.assertEquals(
        "parameter is not supported by chartType: xField",
        exception.getMessage()
    );
  }

  @Test
  void optionsEndpoint_shouldReturnFailureForMissingDataset() throws Exception {
    PlatformException exception = assertThrows(PlatformException.class,
        () -> controller.options("demo-app", "demo.missing", "bar", "yFields"));
    org.junit.jupiter.api.Assertions.assertEquals(
        "dataset definition not found: demo.missing",
        exception.getMessage()
    );
  }

  private DatasetFieldDefinition field(LowcodeDatasetDefinition dataset, String name) {
    return dataset.getSchema().getFields().stream()
        .filter(field -> name.equals(field.getName()))
        .findFirst()
        .orElseThrow(IllegalArgumentException::new);
  }
}
