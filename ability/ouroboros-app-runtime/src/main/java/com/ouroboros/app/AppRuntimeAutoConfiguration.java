package com.ouroboros.app;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EventObject;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;

import com.ouroboros.AppContext;
import com.ouroboros.app.runtime.DefaultResolvedRuntimeLockProvider;
import com.ouroboros.app.runtime.ResolvedRuntimeLockProvider;
import com.ouroboros.app.runtime.RuntimeLockConsumptionException;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.lifecycle.event.OuroborosBootstrapFailedEvent;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshEvent;
import com.ouroboros.mq.MQBridge;
import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

@EnableCaching
@Configuration
@Import(WebApiResultHttpStatusAdvice.class)
@ComponentScan
@ComponentScan("com.ouroboros.common.app.util")
public class AppRuntimeAutoConfiguration {
  private final static Logger logger = LoggerFactory.getLogger(AppRuntimeAutoConfiguration.class);
  @Value("${ouroboros.metadata.path:${ouroboros.app-root:./ouroboros}}")
  private String homePath;

  @Value(value = "${spring.application.name:default}")
  @Nullable
  String appName;

  @Value(value = "${spring.profiles.active:default}")
  @Nullable
  String profile;

  @Value(value = "${ouroboros.runtime.kind:TEST_APP}")
  @Nullable
  String runtimeKind;

  @Autowired
  ApplicationEventPublisher eventPublisher;

  @Autowired
  ApplicationEventPublisher publisher;


  @Bean("publicApiAnonymousAccessApiProvider")
  public AnonymousApiProvider getAnonymousAccessApiProvider() {
    return () -> Collections.singletonList("* /public-api/**");
  }

  @Bean("errorDispatchAnonymousAccessApiProvider")
  public AnonymousApiProvider getErrorDispatchAnonymousAccessApiProvider() {
    return () -> Collections.singletonList("* /error");
  }

  @Bean
  public AppContext getAppContext() throws IOException {
    File homeFile = this.homePath.startsWith(".")
        ? new File(System.getProperty("user.dir"), this.homePath.substring(1))
        : new File(this.homePath);

    if (!homeFile.exists() || !homeFile.isDirectory()) {
      Files.createDirectories(Paths.get(homePath));
    }

    return homeFile::getPath;
  }

  @Bean
  public ResolvedRuntimeLockProvider resolvedRuntimeLockProvider(AppContext appContext) {
    return new DefaultResolvedRuntimeLockProvider(appContext, appName, profile, runtimeKind);
  }

  @Bean("runtimeLockStartupInput")
  public Map<String, Object> runtimeLockStartupInput(ResolvedRuntimeLockProvider resolvedRuntimeLockProvider,
                                                     @Value("${OUROBOROS_RUNTIME_LOCK_REQUIRED:false}") boolean runtimeLockRequired) {
    if (!runtimeLockRequired) {
      return Collections.emptyMap();
    }
    Optional<Map<String, Object>> latestLockInput = resolvedRuntimeLockProvider.findLatestResolvedArtifactInput();
    if (!latestLockInput.isPresent()) {
      throw new RuntimeLockConsumptionException(
          String.format("运行时要求读取最新依赖锁但未找到: app=%s, profile=%s, runtimeKind=%s", appName, profile, runtimeKind)
      );
    }
    return latestLockInput.get();
  }

  @Autowired
  public void initOuroborosAppControlListener(MQBridge mqBridge) {
    mqBridge.bindingConsumer("ouroboros-app-control")
        .destination("ouroboros.app.control")
        .build();
    mqBridge.subscribe("ouroboros-app-control", this::controlMessageHandler);
  }

  @EventListener({ApplicationStartedEvent.class, OuroborosContextRefreshEvent.class})
  void onOuroborosContextRefreshEvent(EventObject event) {
    synchronized (this) {
      logger.info("Ouroboros Context Refresh");
      try {
        OuroborosContextRefreshLifecycle.publish(event, e -> publisher.publishEvent(e));
        // 输出 banner
        printBanner();
        logger.info("Ouroboros Context Refresh Finished");
      } catch (RuntimeException e) {
        logger.error("Ouroboros Context Refresh Failed", e);
        try {
          publisher.publishEvent(OuroborosBootstrapFailedEvent.fallback(event, "context-refresh", "app-runtime", e));
        } catch (RuntimeException failedEventPublishFailure) {
          e.addSuppressed(failedEventPublishFailure);
          logger.error("Ouroboros Bootstrap Failed Event Publish Failed", failedEventPublishFailure);
        }
        throw e;
      }
    }
  }

  @PostConstruct
  public void init() {
    logger.info("Ouroboros App Runtime AutoConfiguration loaded");
  }


  void controlMessageHandler(Message<?> message) {
    var messageBody = new String((byte[]) message.getPayload(), StandardCharsets.UTF_8);
    Map<String, Object> messageMap = Json.toMap(messageBody);
    var command = messageMap.get("command");
    var appAndProfile = appName + "-" + profile;
    if ("refresh".equals(command) && appAndProfile.equals(messageMap.get("appAndProfile"))) {
      eventPublisher.publishEvent(new OuroborosContextRefreshEvent(this));
    }
  }


  void printBanner() {
    Consumer<String> printer = logger::info;
    ResourceUtil.getResourceContentStream("META-INF/banner_ascii_art.txt")
        .map(s -> s.contains("{version}") ? s.replace("{version}", getVersion()) : s)
        .forEach(printer);
  }

  String getVersion() {
    var props = new Properties();
    try {
      var inStream = getClass().getResourceAsStream("/META-INF/maven/com.ouroboros/ouroboros-app-runtime/pom.properties");
      if (inStream == null) {
        return "unknown";
      }
      props.load(inStream);
      return props.getProperty("version");
    } catch (IOException e) {
      return "unknown";
    }
  }

}
