package com.ouroboros.statistics.reporting.dev.ui.provider;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsReportingUISchemaProviderTest {

  @Test
  void shouldProvideFlatDatasetAndChartSchemas() {
    StatisticsReportingUISchemaProvider provider = new StatisticsReportingUISchemaProvider();

    assertTrue(provider.getUISchema("/dev/{appName}/dataset").isPresent());
    assertTrue(provider.getUISchema("/dev/{appName}/dataset/add").isPresent());
    assertTrue(provider.getUISchema("/dev/{appName}/dataset/{datasetName}").isPresent());
    assertTrue(provider.getUISchema("/dev/{appName}/chart").isPresent());
    assertTrue(provider.getUISchema("/dev/{appName}/chart/add").isPresent());
    assertTrue(provider.getUISchema("/dev/{appName}/chart/{fullName}").isPresent());
  }

  @Test
  void shouldRouteDatasetAndChartPagesToDedicatedSchemas() {
    StatisticsReportingUISchemaProvider provider = new StatisticsReportingUISchemaProvider();

    Object datasetList = provider.getUISchema("/dev/{appName}/dataset").get();
    Object datasetAdd = provider.getUISchema("/dev/{appName}/dataset/add").get();
    Object datasetEdit = provider.getUISchema("/dev/{appName}/dataset/{datasetName}").get();
    Object chartList = provider.getUISchema("/dev/{appName}/chart").get();
    Object chartAdd = provider.getUISchema("/dev/{appName}/chart/add").get();
    Object chartEdit = provider.getUISchema("/dev/{appName}/chart/{fullName}").get();

    assertNotEquals(datasetList, datasetAdd);
    assertNotEquals(datasetList, datasetEdit);
    assertNotEquals(chartList, chartAdd);
    assertNotEquals(chartList, chartEdit);
  }

  @Test
  void shouldExposeOnlyFlatAuthoringSchemasInProviderMap() {
    StatisticsReportingUISchemaProvider provider = new StatisticsReportingUISchemaProvider();

    assertTrue(provider.getUISchemaMap().containsKey("/dev/{appName}/dataset"));
    assertTrue(provider.getUISchemaMap().containsKey("/dev/{appName}/dataset/add"));
    assertTrue(provider.getUISchemaMap().containsKey("/dev/{appName}/dataset/{datasetName}"));
    assertTrue(provider.getUISchemaMap().containsKey("/dev/{appName}/chart"));
    assertTrue(provider.getUISchemaMap().containsKey("/dev/{appName}/chart/add"));
    assertTrue(provider.getUISchemaMap().containsKey("/dev/{appName}/chart/{fullName}"));
    assertFalse(provider.getUISchemaMap().containsKey("/dev/{appName}/dataset/{fullName}"));
  }

  @Test
  void shouldNotExposeLegacyAnalyticsOrStatisticsReportingAuthoringRoutes() {
    StatisticsReportingUISchemaProvider provider = new StatisticsReportingUISchemaProvider();

    assertFalse(provider.getUISchema("/dev/{appName}/analytics").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/analytics/datasets").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/analytics/datasets/add").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/analytics/datasets/{fullName}").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/analytics/datasets/{datasetName}/studio").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/analytics/charts").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/analytics/dashboards").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/analytics/dashboards/{fullName}/designer").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/statistics-reporting").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/statistics-analysis").isPresent());
    assertFalse(provider.getUISchema("/dev/{appName}/statistics-reporting/datasets/{datasetName}/studio").isPresent());
  }
}
