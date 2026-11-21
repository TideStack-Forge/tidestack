package com.xinpai.ouroboros.compatible;

import com.xinpai.ouroboros.AppContext;
import com.xinpai.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.xinpai.ouroboros.lifecycle.event.OuroborosContextRefreshEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.EventObject;

/**
 * @author liansz
 * @createTime 2022/12/1 18:09
 */
@Configuration
@ComponentScan
public class AutoConfiguration {
  @Autowired
  ApplicationEventPublisher publisher;

  private final Logger logger = LoggerFactory.getLogger(AutoConfiguration.class);

  @Value("${ouroboros.resource.models.root:app}")
  private String homePath;

  @PostConstruct
  public void afterPropertiesSet() {
    logger.debug("xinpai ouroboros-compatible-qb-spring-boot-starter loaded");
  }

  @Bean
  public AppContext getAppContext() {
    String path = System.getProperty("user.dir");
    File homeDir = new File(path, homePath);
    if (!homeDir.exists()) {
      homeDir.mkdirs();
    }

    logger.debug("homeDir: " + homeDir.getPath());
    return homeDir::getPath;
  }

  @Bean
  public WebServerFactoryCustomizer<TomcatServletWebServerFactory> containerCustomizer(){
    return factory -> factory.addConnectorCustomizers(connector -> connector.setProperty("relaxedQueryChars", "|{}[]"));
  }

  @EventListener({ApplicationStartedEvent.class, OuroborosContextRefreshEvent.class})
  void onOuroborosContextRefreshEvent(EventObject event) {
    synchronized (this) {
      logger.info("Ouroboros Context Refresh");
      OuroborosContextRefreshLifecycle.publish(this, e -> publisher.publishEvent(e));
    }
  }
}
