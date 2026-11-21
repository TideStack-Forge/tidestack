package com.ouroboros.statistics.reporting.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.AppContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StatisticsReportingRuntimeFinalUserContractTest {

  @Test
  void shouldExposeChartRuntimeExecuteEndpoint() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(loadRuntimeAutoConfigurationClass(), TestConfig.class);
      context.refresh();

      Set<String> endpoints = collectHttpEndpoints(context);

      assertTrue(
          endpoints.contains("POST /api/runtime/statistics-reporting/charts/{chartName}/execute"),
          "missing chart final-user runtime endpoint; found: " + endpoints
      );
    } finally {
      context.close();
    }
  }

  @Test
  void shouldExposeChartRuntimeExportEndpoint() throws Exception {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.register(loadRuntimeAutoConfigurationClass(), TestConfig.class);
      context.refresh();

      Set<String> endpoints = collectHttpEndpoints(context);

      assertTrue(
          endpoints.contains("POST /api/runtime/statistics-reporting/charts/{chartName}/export"),
          "missing chart runtime export endpoint; found: " + endpoints
      );
    } finally {
      context.close();
    }
  }

  private Class<?> loadRuntimeAutoConfigurationClass() throws Exception {
    return Class.forName("com.ouroboros.statistics.reporting.runtime.StatisticsReportingRuntimeAutoConfiguration");
  }

  private static Set<String> collectHttpEndpoints(AnnotationConfigApplicationContext context) {
    Set<String> endpoints = new LinkedHashSet<String>();
    for (String beanName : context.getBeanDefinitionNames()) {
      Class<?> beanType = context.getType(beanName);
      if (beanType == null || !beanType.isAnnotationPresent(RestController.class)) {
        continue;
      }
      List<String> classPaths = requestPaths(beanType.getAnnotation(RequestMapping.class));
      if (classPaths.isEmpty()) {
        classPaths.add("");
      }
      for (Method method : beanType.getDeclaredMethods()) {
        collectMapping(endpoints, classPaths, method, method.getAnnotation(GetMapping.class), "GET");
        collectMapping(endpoints, classPaths, method, method.getAnnotation(PostMapping.class), "POST");
      }
    }
    return endpoints;
  }

  private static void collectMapping(Set<String> endpoints,
                                     List<String> classPaths,
                                     Method method,
                                     Annotation annotation,
                                     String httpMethod) {
    if (annotation == null) {
      return;
    }
    List<String> methodPaths = mappingPaths(annotation);
    if (methodPaths.isEmpty()) {
      methodPaths.add("");
    }
    for (String classPath : classPaths) {
      for (String methodPath : methodPaths) {
        endpoints.add(httpMethod + " " + joinPath(classPath, methodPath));
      }
    }
  }

  private static List<String> requestPaths(RequestMapping mapping) {
    if (mapping == null) {
      return new ArrayList<String>();
    }
    List<String> paths = new ArrayList<String>();
    paths.addAll(Arrays.asList(mapping.path()));
    paths.addAll(Arrays.asList(mapping.value()));
    return paths.stream().filter(path -> path != null && !path.isEmpty()).collect(Collectors.toList());
  }

  private static List<String> mappingPaths(Annotation annotation) {
    List<String> paths = new ArrayList<String>();
    if (annotation instanceof GetMapping) {
      GetMapping mapping = (GetMapping) annotation;
      paths.addAll(Arrays.asList(mapping.path()));
      paths.addAll(Arrays.asList(mapping.value()));
    }
    if (annotation instanceof PostMapping) {
      PostMapping mapping = (PostMapping) annotation;
      paths.addAll(Arrays.asList(mapping.path()));
      paths.addAll(Arrays.asList(mapping.value()));
    }
    return paths.stream().filter(path -> path != null && !path.isEmpty()).collect(Collectors.toList());
  }

  private static String joinPath(String classPath, String methodPath) {
    String left = classPath == null ? "" : classPath.trim();
    String right = methodPath == null ? "" : methodPath.trim();
    if (left.endsWith("/")) {
      left = left.substring(0, left.length() - 1);
    }
    if (!right.isEmpty() && !right.startsWith("/")) {
      right = "/" + right;
    }
    if (left.isEmpty()) {
      return right.isEmpty() ? "/" : right;
    }
    return right.isEmpty() ? left : left + right;
  }

  @Configuration
  static class TestConfig {
    @Bean
    public DataSource reportingDataSource() {
      JdbcDataSource dataSource = new JdbcDataSource();
      dataSource.setURL("jdbc:h2:mem:statistics_reporting_runtime_final_user_contract;MODE=MYSQL;DB_CLOSE_DELAY=-1");
      dataSource.setUser("sa");
      dataSource.setPassword("");
      return dataSource;
    }

    @Bean
    public AppContext appContext() {
      return () -> java.nio.file.Paths.get("target", "statistics-reporting-runtime-final-user-contract")
          .toAbsolutePath()
          .toString();
    }
  }
}
