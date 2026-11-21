package com.ouroboros.statistics.reporting;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StatisticsReportingModuleSmokeTest {

  @Test
  void shouldLoadDatasetDefinitionType() {
    assertNotNull(DatasetDefinition.class);
  }
}
