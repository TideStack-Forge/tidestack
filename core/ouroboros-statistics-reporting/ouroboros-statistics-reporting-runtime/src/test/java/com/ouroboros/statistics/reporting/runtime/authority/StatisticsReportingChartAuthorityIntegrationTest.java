package com.ouroboros.statistics.reporting.runtime.authority;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ouroboros.AppContext;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshingEvent;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.rbac.provider.SpringBeanAuthorityProvider;
import com.ouroboros.statistics.reporting.definition.view.ChartDefinition;
import com.ouroboros.statistics.reporting.runtime.StatisticsReportingRuntimeAutoConfiguration;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsReportingChartAuthorityIntegrationTest {
  private static final String AUTHORITY_NAME = "statisticsReporting.runtime.chart.demo.sales-summary.execute";

  @TempDir
  Path appHome;

  @Test
  void shouldExposeOnlyTheEffectiveChartAuthorityThroughThePlatformSpiBridge() throws Exception {
    writeChart("statistics-reporting/charts", "Default", true);
    writeChart("data/statistics-reporting/charts", "Override", true);

    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(AppContext.class, () -> () -> appHome.toString());
      context.registerBean(DataSource.class, JdbcDataSource::new);
      context.register(StatisticsReportingRuntimeAutoConfiguration.class, SpringBeanAuthorityProvider.class);
      context.refresh();

      Authority authority = AuthorityCenter.getAuthority(AUTHORITY_NAME).orElseThrow();
      assertEquals("Override · 查询", authority.getTitle());
      assertEquals(List.of(
          "POST /api/runtime/statistics-reporting/charts/{chartName:(?i)\\Qdemo.sales-summary\\E}/execute"), authority.getApis());
      assertEquals("Override · 查询", AuthorityCenter.getAllAuthority().get(AUTHORITY_NAME).getTitle());

      writeChart("data/statistics-reporting/charts", "Refreshed", true);
      context.publishEvent(new OuroborosMetadataRefreshingEvent(this));
      assertEquals("Refreshed · 查询", AuthorityCenter.getAuthority(AUTHORITY_NAME).orElseThrow().getTitle());

      Path override = appHome.resolve("data/statistics-reporting/charts/demo.sales-summary.chart.json");
      Files.writeString(override, "{broken");
      assertThrows(PlatformException.class, () -> context.publishEvent(new OuroborosMetadataRefreshingEvent(this)));
      assertEquals("Refreshed · 查询", AuthorityCenter.getAuthority(AUTHORITY_NAME).orElseThrow().getTitle());

      writeChart("data/statistics-reporting/charts", "Disabled override", false);
      context.publishEvent(new OuroborosMetadataRefreshingEvent(this));
      assertTrue(AuthorityCenter.getAuthority(AUTHORITY_NAME).isEmpty());
      assertTrue(!AuthorityCenter.getAllAuthority().containsKey(AUTHORITY_NAME));

      Files.delete(override);
      context.publishEvent(new OuroborosMetadataRefreshingEvent(this));
      assertEquals("Default · 查询", AuthorityCenter.getAuthority(AUTHORITY_NAME).orElseThrow().getTitle());
    } finally {
      // The platform SPI bridge is static; detach the closed test context.
      new SpringBeanAuthorityProvider();
    }
  }

  private void writeChart(String directory, String title, boolean enabled) throws Exception {
    ChartDefinition chart = new ChartDefinition();
    chart.setName("demo.sales-summary");
    chart.setTitle(title);
    chart.setIsEnabled(enabled);
    Path file = appHome.resolve(directory).resolve(chart.getName() + ".chart.json");
    Files.createDirectories(file.getParent());
    Files.writeString(file, Json.toPrettyJsonString(chart));
  }
}
