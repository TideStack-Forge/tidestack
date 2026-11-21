package com.ouroboros.statistics.reporting.runtime;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.AppContext;
import com.ouroboros.statistics.reporting.runtime.center.DefaultDatasetDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.center.DefaultChartDefinitionCenter;
import com.ouroboros.statistics.reporting.runtime.controller.CompositeViewExecutionController;
import com.ouroboros.statistics.reporting.runtime.controller.DatasetExecutionController;
import com.ouroboros.statistics.reporting.runtime.controller.ChartExecutionController;
import com.ouroboros.statistics.reporting.runtime.chart.CartesianChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.service.ChartCompiler;
import com.ouroboros.statistics.reporting.runtime.service.ChartExecutionService;
import com.ouroboros.statistics.reporting.runtime.chart.ChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.ChartOptionBuilderRegistry;
import com.ouroboros.statistics.reporting.runtime.chart.ChartParameterParser;
import com.ouroboros.statistics.reporting.runtime.chart.DataChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.PieChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.SankeyChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.chart.ScatterChartOptionBuilder;
import com.ouroboros.statistics.reporting.runtime.provider.AppDefaultDatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.provider.AppDefaultChartDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetExecutionContextResolver;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetExecutionService;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetParameterNormalizer;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultSqlDialectResolver;
import com.ouroboros.statistics.reporting.runtime.execution.JdbcSqlExecutionBackend;
import com.ouroboros.statistics.reporting.runtime.execution.SpringSqlDataSourceProvider;
import com.ouroboros.statistics.reporting.runtime.execution.SqlDataSourceProvider;
import com.ouroboros.statistics.reporting.runtime.execution.SqlExecutionBackend;
import com.ouroboros.statistics.reporting.runtime.executor.SqlDatasetExecutor;
import com.ouroboros.statistics.reporting.runtime.projector.ChartProjector;
import com.ouroboros.statistics.reporting.runtime.provider.DatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.provider.ChartDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.provider.RuntimeOverrideDatasetDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.provider.RuntimeOverrideChartDefinitionProvider;
import com.ouroboros.statistics.reporting.runtime.authority.StatisticsReportingDefinitionAuthorityProvider;
import com.ouroboros.statistics.reporting.validation.ChartDefinitionValidator;

@Configuration
public class StatisticsReportingRuntimeAutoConfiguration {
  private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsReportingRuntimeAutoConfiguration.class);

  @PostConstruct
  void init() {
    LOGGER.info("Ouroboros Statistics Reporting Runtime Auto Configuration loaded");
  }

  @Bean
  @ConditionalOnMissingBean
  public DefaultDatasetDefinitionCenter datasetDefinitionCenter(ObjectProvider<DatasetDefinitionProvider> providers) {
    return new DefaultDatasetDefinitionCenter(providers.orderedStream().collect(Collectors.toList()));
  }

  @Bean
  @ConditionalOnMissingBean
  public DefaultChartDefinitionCenter chartDefinitionCenter(ObjectProvider<ChartDefinitionProvider> providers) {
    return new DefaultChartDefinitionCenter(providers.orderedStream().collect(Collectors.toList()));
  }

  @Bean
  @ConditionalOnMissingBean
  public StatisticsReportingDefinitionAuthorityProvider statisticsReportingDefinitionAuthorityProvider(
      DefaultChartDefinitionCenter chartDefinitionCenter, DefaultDatasetDefinitionCenter datasetDefinitionCenter) {
    return new StatisticsReportingDefinitionAuthorityProvider(chartDefinitionCenter, datasetDefinitionCenter);
  }

  @Bean
  @ConditionalOnMissingBean
  public AppDefaultDatasetDefinitionProvider appDefaultDatasetDefinitionProvider(AppContext appContext) {
    return new AppDefaultDatasetDefinitionProvider(appContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public AppDefaultChartDefinitionProvider appDefaultChartDefinitionProvider(AppContext appContext) {
    return new AppDefaultChartDefinitionProvider(appContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public RuntimeOverrideDatasetDefinitionProvider runtimeOverrideDatasetDefinitionProvider(AppContext appContext) {
    return new RuntimeOverrideDatasetDefinitionProvider(appContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public RuntimeOverrideChartDefinitionProvider runtimeOverrideChartDefinitionProvider(AppContext appContext) {
    return new RuntimeOverrideChartDefinitionProvider(appContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public DefaultDatasetParameterNormalizer defaultDatasetParameterNormalizer() {
    return new DefaultDatasetParameterNormalizer();
  }

  @Bean
  @ConditionalOnMissingBean(DatasetPermissionInjector.class)
  public DefaultDatasetPermissionInjector datasetPermissionInjector() {
    return new DefaultDatasetPermissionInjector();
  }

  @Bean
  @ConditionalOnMissingBean
  public DatasetExecutionContextResolver datasetExecutionContextResolver(AppContext appContext) {
    return new DefaultDatasetExecutionContextResolver(appContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public DefaultSqlDialectResolver defaultSqlDialectResolver() {
    return new DefaultSqlDialectResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  public ChartDefinitionValidator chartDefinitionValidator() {
    return new ChartDefinitionValidator();
  }

  @Bean
  @ConditionalOnMissingBean
  public ChartParameterParser chartParameterParser() {
    return new ChartParameterParser();
  }

  @Bean
  @ConditionalOnMissingBean
  public ChartProjector chartProjector() {
    return new ChartProjector();
  }

  @Bean
  @ConditionalOnMissingBean
  public CartesianChartOptionBuilder cartesianChartOptionBuilder() {
    return new CartesianChartOptionBuilder();
  }

  @Bean
  @ConditionalOnMissingBean
  public PieChartOptionBuilder pieChartOptionBuilder() {
    return new PieChartOptionBuilder();
  }

  @Bean
  @ConditionalOnMissingBean
  public ScatterChartOptionBuilder scatterChartOptionBuilder() {
    return new ScatterChartOptionBuilder();
  }

  @Bean
  @ConditionalOnMissingBean
  public SankeyChartOptionBuilder sankeyChartOptionBuilder() {
    return new SankeyChartOptionBuilder();
  }

  @Bean
  @ConditionalOnMissingBean
  public DataChartOptionBuilder dataChartOptionBuilder() {
    return new DataChartOptionBuilder();
  }

  @Bean
  @ConditionalOnMissingBean
  public ChartOptionBuilderRegistry chartOptionBuilderRegistry(ObjectProvider<ChartOptionBuilder> builders) {
    return new ChartOptionBuilderRegistry(builders.orderedStream().collect(Collectors.toList()));
  }

  @Bean
  @ConditionalOnMissingBean
  public ChartCompiler chartCompiler(ChartDefinitionValidator validator,
                                     ChartParameterParser parameterParser,
                                     ChartProjector chartProjector,
                                     ChartOptionBuilderRegistry optionBuilderRegistry) {
    return new ChartCompiler(validator, parameterParser, chartProjector, optionBuilderRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public SqlDataSourceProvider sqlDataSourceProvider(Map<String, javax.sql.DataSource> dataSources,
                                                     ObjectProvider<javax.sql.DataSource> uniqueDataSourceProvider) {
    return new SpringSqlDataSourceProvider(dataSources, uniqueDataSourceProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  public SqlExecutionBackend sqlExecutionBackend() {
    return new JdbcSqlExecutionBackend();
  }

  @Bean
  @ConditionalOnMissingBean
  public SqlDatasetExecutor sqlDatasetExecutor(DefaultSqlDialectResolver defaultSqlDialectResolver,
                                               SqlDataSourceProvider sqlDataSourceProvider,
                                               SqlExecutionBackend sqlExecutionBackend) {
    return new SqlDatasetExecutor(defaultSqlDialectResolver, sqlDataSourceProvider, sqlExecutionBackend);
  }

  @Bean
  @ConditionalOnMissingBean(DatasetExecutionService.class)
  public DatasetExecutionService datasetExecutionService(DefaultDatasetDefinitionCenter datasetDefinitionCenter,
                                                         DefaultDatasetParameterNormalizer parameterNormalizer,
                                                         DatasetPermissionInjector datasetPermissionInjector,
                                                         DatasetExecutionContextResolver datasetExecutionContextResolver,
                                                         ObjectProvider<com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor> datasetExecutors) {
    List<com.ouroboros.statistics.reporting.runtime.executor.DatasetExecutor> executors =
        datasetExecutors.orderedStream().collect(Collectors.toList());
    return new DefaultDatasetExecutionService(
        datasetDefinitionCenter,
        parameterNormalizer,
        datasetPermissionInjector,
        executors,
        datasetExecutionContextResolver
    );
  }

  @Bean
  @ConditionalOnMissingBean
  public ChartExecutionService chartExecutionService(DefaultDatasetDefinitionCenter datasetDefinitionCenter,
                                                     DefaultChartDefinitionCenter chartDefinitionCenter,
                                                     DatasetExecutionService datasetExecutionService,
                                                     ChartCompiler chartCompiler) {
    return new ChartExecutionService(
        datasetDefinitionCenter,
        chartDefinitionCenter,
        datasetExecutionService,
        chartCompiler
    );
  }

  @Bean
  @ConditionalOnMissingBean
  public DatasetExecutionController datasetExecutionController(DefaultDatasetDefinitionCenter datasetDefinitionCenter,
                                                               DatasetExecutionService datasetExecutionService,
                                                               DatasetPermissionInjector datasetPermissionInjector,
                                                               DatasetExecutionContextResolver datasetExecutionContextResolver) {
    return new DatasetExecutionController(
        datasetDefinitionCenter,
        datasetExecutionService,
        datasetPermissionInjector::canExport,
        datasetExecutionContextResolver
    );
  }

  @Bean
  @ConditionalOnMissingBean
  public ChartExecutionController chartExecutionController(DefaultChartDefinitionCenter chartDefinitionCenter,
                                                                      DefaultDatasetDefinitionCenter datasetDefinitionCenter,
                                                                      DatasetExecutionService datasetExecutionService,
                                                                      ChartExecutionService chartExecutionService,
                                                                      ChartDefinitionValidator chartDefinitionValidator,
                                                                      DatasetPermissionInjector datasetPermissionInjector,
                                                                      DatasetExecutionContextResolver datasetExecutionContextResolver) {
    return new ChartExecutionController(
        datasetDefinitionCenter,
        chartDefinitionCenter,
        datasetExecutionService,
        chartExecutionService,
        chartDefinitionValidator,
        datasetPermissionInjector::canExport,
        datasetExecutionContextResolver
    );
  }

  @Bean
  @ConditionalOnMissingBean
  public CompositeViewExecutionController compositeViewExecutionController(DatasetExecutionService datasetExecutionService,
                                                                           DatasetExecutionContextResolver datasetExecutionContextResolver) {
    return new CompositeViewExecutionController(datasetExecutionService, datasetExecutionContextResolver);
  }
}
