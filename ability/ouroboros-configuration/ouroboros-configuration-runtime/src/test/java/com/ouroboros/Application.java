package com.ouroboros;

import java.io.IOException;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ouroboros.configuration.center.ConfigurationGroupMetadataCenter;
import com.ouroboros.configuration.center.ConfigurationMetadataCenter;
import com.ouroboros.configuration.provider.FileConfigurationMetadataProvider;
import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.station.DataStationCenter;
import com.ouroboros.data.typed.runtime.ModelRefreshedListener;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.webapi.WebAPIModelInitializer;

/**
 * @author liansz
 */
@EnableCaching
@DataModelScan("com.ouroboros.configuration.model")
@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class);
  }

  @Bean
  public AppContext getAppContext() throws IOException {
    String homePath = new ClassPathResource("test-app").getFile().getAbsolutePath();
    return () -> homePath;
  }

  @Bean
  public CommandLineRunner initializeTestRuntime(
      FileConfigurationMetadataProvider provider,
      JdbcTemplate jdbcTemplate,
      ModelRefreshedListener modelRefreshedListener,
      WebAPIModelInitializer webAPIModelInitializer) {
    return args -> {
      jdbcTemplate.execute("create table if not exists \"t_sys_configuration_value\" ("
          + "\"key\" varchar(255) primary key, "
          + "\"value\" varchar(255), "
          + "\"revision\" bigint, "
          + "\"generation\" varchar(36), "
          + "\"update_at\" timestamp, "
          + "\"update_by\" varchar(255))");
      ConfigurationMetadataCenter.refresh();
      ConfigurationGroupMetadataCenter.refresh();
      DataStationCenter.refresh();
      DataModelCenter.refresh();
      modelRefreshedListener.onModelRefreshed();
      webAPIModelInitializer.onOuroborosContextRefreshed(new OuroborosContextRefreshedEvent(this));
    };
  }
}
