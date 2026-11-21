package com.ouroboros.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.mothership.OuroborosApplication;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.util.Json;

/** Local deployment fixture; adds no business endpoints or alternative execution path. */
public class BehaviorAcceptanceApplication {
  public static void main(String[] args) {
    SpringApplication.run(new Class<?>[]{OuroborosApplication.class, Deployment.class}, args);
  }

  @Configuration(proxyBeanMethods = false)
  public static class Deployment {
    @Bean
    FilterRegistrationBean<CorsFilter> acceptanceCors(@Value("${acceptance.frontend-origin}") String origin) {
      var cors = new CorsConfiguration();
      cors.setAllowedOrigins(List.of(origin));
      cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
      cors.setAllowedHeaders(List.of("*"));
      cors.setAllowCredentials(true);
      var source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/api/**", cors);
      var filter = new FilterRegistrationBean<>(new CorsFilter(source));
      filter.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
      return filter;
    }

    @Bean
    EvidenceObserver evidenceObserver(@Value("${acceptance.evidence-dir}") Path directory) {
      return new EvidenceObserver(directory);
    }
  }

  /** Reads synthetic acceptance records through the official DataModel API. */
  public record EvidenceObserver(Path directory) {
    @Scheduled(fixedDelay = 1000)
    public void snapshot() throws java.io.IOException {
      if (DataModelCenter.getDataModel("ouroboros.BusinessEventOccurrence").isEmpty()) {
        return;
      }
      Files.createDirectories(directory);
      CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), () -> {
        write("facts", "ouroboros.BusinessEventOccurrence", List.of("id", "modelFullName", "eventName"),
            Map.of("modelFullName", "BehaviorOrder"));
        write("deliveries", "ouroboros.BusinessEventDelivery", List.of("id", "eventId", "consumerModelFullName",
            "subscriptionName", "status", "attempt", "lastError"), Map.of("consumerModelFullName", "BehaviorOrder"));
        return null;
      });
    }

    private void write(String file, String model, List<String> fields, Map<String, Object> where) {
      var rows = DataModelCenter.getDataModel(model).orElseThrow().query().select(fields)
          .where(where).clause("LIMIT", 100).execute().get();
      try {
        Files.writeString(directory.resolve(file + ".json"), Json.toJsonString(rows));
      } catch (java.io.IOException failure) {
        throw new java.io.UncheckedIOException(failure);
      }
    }
  }
}
