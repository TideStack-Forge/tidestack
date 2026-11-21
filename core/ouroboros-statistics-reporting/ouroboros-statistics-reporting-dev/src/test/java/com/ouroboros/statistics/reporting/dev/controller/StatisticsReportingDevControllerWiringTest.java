package com.ouroboros.statistics.reporting.dev.controller;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.statistics.reporting.definition.DatasetDefinition;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetExecutionRequest;
import com.ouroboros.statistics.reporting.runtime.execution.DatasetPermissionInjector;
import com.ouroboros.statistics.reporting.runtime.execution.DefaultDatasetParameterNormalizer;
import com.ouroboros.statistics.reporting.dev.model.DatasetDevConfigModel;
import com.ouroboros.statistics.reporting.dev.service.DatasetDevConfigService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class StatisticsReportingDevControllerWiringTest {

  @Test
  void shouldInjectExplicitPermissionAndNormalizerBeansWhenContextRefreshes() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(TestConfig.class, StatisticsReportingDevController.class);
      context.refresh();

      StatisticsReportingDevController controller = context.getBean(StatisticsReportingDevController.class);
      assertNotNull(controller);
      assertSame(context.getBean(DefaultDatasetParameterNormalizer.class),
          readField(controller, "parameterNormalizer"));
      assertSame(context.getBean(DatasetPermissionInjector.class),
          readField(controller, "datasetPermissionInjector"));
      assertSame(context.getBean(DatasetDevConfigService.class),
          readField(controller, "datasetDevConfigService"));
    } finally {
      context.close();
    }
  }

  @Test
  void shouldUseOrderedDatasetDevConfigServiceWhenMultipleCandidatesExist() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(MultipleDevConfigServiceConfig.class, StatisticsReportingDevController.class);
      context.refresh();

      StatisticsReportingDevController controller = context.getBean(StatisticsReportingDevController.class);
      assertSame(context.getBean("preferredDatasetDevConfigService"),
          readField(controller, "datasetDevConfigService"));
    } finally {
      context.close();
    }
  }

  private Object readField(Object target, String fieldName) throws Exception {
    java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(target);
  }

  static class TestConfig {
    @Bean
    public com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory previewExecutorFactory() {
      return org.mockito.Mockito.mock(com.ouroboros.statistics.reporting.dev.service.DatasetPreviewExecutorFactory.class);
    }

    @Bean
    public AppHomeResolver appHomeResolver() {
      return appId -> "/tmp/" + appId;
    }

    @Bean
    public DefaultDatasetParameterNormalizer defaultDatasetParameterNormalizer() {
      return new DefaultDatasetParameterNormalizer();
    }

    @Bean
    public DatasetPermissionInjector datasetPermissionInjector() {
      return new DatasetPermissionInjector() {
        @Override
        public DatasetExecutionRequest inject(DatasetDefinition dataset, DatasetExecutionRequest request) {
          return request;
        }
      };
    }

    @Bean
    public DatasetDevConfigService datasetDevConfigService() {
      return new DatasetDevConfigService() {
        @Override
        public io.vavr.control.Try<DatasetDevConfigModel> save(DatasetDevConfigModel config) {
          return io.vavr.control.Try.success(config);
        }

        @Override
        public java.util.Optional<DatasetDevConfigModel> findByDataset(String appName, String datasetName) {
          return java.util.Optional.empty();
        }

        @Override
        public java.util.List<DatasetDevConfigModel> findByAppName(String appName) {
          return java.util.Collections.emptyList();
        }
      };
    }
  }

  static class MultipleDevConfigServiceConfig extends TestConfig {
    @Bean
    @Order(0)
    public DatasetDevConfigService preferredDatasetDevConfigService() {
      return devConfigService();
    }

    @Bean
    @Order(1)
    public DatasetDevConfigService secondaryDatasetDevConfigService() {
      return devConfigService();
    }

    private DatasetDevConfigService devConfigService() {
      return new DatasetDevConfigService() {
        @Override
        public io.vavr.control.Try<DatasetDevConfigModel> save(DatasetDevConfigModel config) {
          return io.vavr.control.Try.success(config);
        }

        @Override
        public java.util.Optional<DatasetDevConfigModel> findByDataset(String appName, String datasetName) {
          return java.util.Optional.empty();
        }

        @Override
        public java.util.List<DatasetDevConfigModel> findByAppName(String appName) {
          return java.util.Collections.emptyList();
        }
      };
    }
  }
}
