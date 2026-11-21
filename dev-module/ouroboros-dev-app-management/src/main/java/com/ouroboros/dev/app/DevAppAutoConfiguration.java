package com.ouroboros.dev.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.dev.AppHomeResolver;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.config.DevAppTemplateConfig;
import com.ouroboros.security.authority.accesscontrol.api.AnyAuthenticationApiProvider;
import com.ouroboros.upload.TimerCleanUpScheduler;
import com.ouroboros.upload.service.BlobUploadStorageService;

@DataModelScan
@Configuration
@ComponentScan
@Import(DevAppRuntimeLockAutoConfiguration.class)
@EnableConfigurationProperties(DevAppTemplateConfig.class)
@SuppressWarnings("unused")
public class DevAppAutoConfiguration {

  @Value("${ouroboros.dev.workspaceRoot:${ouroboros-dev.workspace-root:./workspace}}")
  private String workspaceRoot;

  @Value("${ouroboros.dev.debugSupportFilesDir:${ouroboros-dev.debug-support-root:./debug-support}}")
  private String debugSupportRoot;

  @Value("${ouroboros.dev.developSupportFilesDir:${ouroboros-dev.develop-support-root:./develop-support}}")
  private String developSupportRoot;

  private final static Logger logger = LoggerFactory.getLogger(DevAppAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros Dev App Auto Configuration loaded");
  }

  @Bean(name = "devAppUploadStorageService", destroyMethod = "close")
  public BlobUploadStorageService uploadStorageService(DevelopWorkspaceContext developWorkspaceContext, org.springframework.core.env.Environment environment) {
    var timerCleanUpScheduler = new TimerCleanUpScheduler();
    var path = Paths.get(developWorkspaceContext.getDevRootDir());
    var store = com.ouroboros.storage.runtime.StorageBinding.create(environment, "ouroboros.dev.upload.storage",
        () -> com.ouroboros.storage.runtime.StorageConfiguration.filesystem(path.toString()));
    return new BlobUploadStorageService(store, "dev", timerCleanUpScheduler);
  }

  @Bean
  public AppHomeResolver createAppHomeResolver(DevelopWorkspaceContext developWorkspaceContext) {
    return (appName) -> Paths.get(developWorkspaceContext.getDevRootDir(), appName).toString();
  }


  @Bean
  public DevelopWorkspaceContext createWorkspaceContext() throws IOException {
    var devWorkspacePath = Paths.get(absPath(workspaceRoot), "dev");
    var runWorkspacePath = Paths.get(absPath(workspaceRoot), "run");
    var debugSupportFilesPath = Paths.get(absPath(debugSupportRoot));
    var developSupportFilesPath = Paths.get(absPath(developSupportRoot));

    if (!Files.exists(devWorkspacePath)) {
      Files.createDirectories(devWorkspacePath);
    }
    if (!Files.exists(debugSupportFilesPath)) {
      Files.createDirectories(debugSupportFilesPath);
    }
    if (!Files.exists(developSupportFilesPath)) {
      Files.createDirectories(developSupportFilesPath);
    }
    if (!Files.exists(runWorkspacePath)) {
      Files.createDirectories(runWorkspacePath);
    }

    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return workspaceRoot;
      }

      @Override
      public String getDevRootDir() {
        return devWorkspacePath.toString();
      }

      @Override
      public String getRunRootDir() {
        return runWorkspacePath.toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return debugSupportFilesPath.toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return developSupportFilesPath.toString();
      }
    };
  }


  /**
   * "./" 开头的相对路径转换为绝对路径
   *
   * @param path
   * @return
   */
  private String absPath(String path) {
    if (path.startsWith(".")) {
      return Paths.get(System.getProperty("user.dir"), path.substring(1)).toString();
    }

    return path;
  }

  @Bean
  public WebMvcConfigurer createWebMvcConfigurer() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "DELETE", "PUT", "PATCH")
            .allowedHeaders("Content-Type", "Authorization")
            .allowCredentials(true)
            .maxAge(3600);
        // 可以继续添加其他URL规则:
        // registry.addMapping("/rest/v2/**")...
      }
    };
  }

  @Bean("developAuthenticatedAccessApiProvider")
  public AnyAuthenticationApiProvider getAuthenticatedAccessApiProvider() {
    List<String> methods = Arrays.asList("GET", "POST", "DELETE", "PUT", "PATCH");
    List<String> urls = Arrays.asList(
        "/api/dev/**",
        "/api/debug-support/**",
        "/api/app/**"
    );

    return () -> methods.stream()
        .flatMap(method -> urls.stream().map(url -> method + " " + url))
        .collect(Collectors.toList());
  }
}
