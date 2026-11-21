package com.ouroboros.dev.runtime.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.workspace.PvcWorkspaceProvisioner;

class KubernetesRuntimeAutoConfigurationTest {

  @Test
  void shouldDiscoverAutoConfigurationThroughSpringFactories() {
    List<String> factoryNames = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class,
        Thread.currentThread().getContextClassLoader()
    );

    assertTrue(factoryNames.contains(KubernetesRuntimeAutoConfiguration.class.getName()));
  }

  @Test
  void shouldContributeKubernetesPropertiesAndPvcWorkspaceProvisioner() {
    new ApplicationContextRunner()
        .withBean(DevelopWorkspaceContext.class, FakeDevelopWorkspaceContext::new)
        .withPropertyValues(
            "ouroboros.dev.runtime.kubernetes.namespace=dev-space",
            "ouroboros.dev.runtime.kubernetes.shared-pvc-name=dev-runtime-pvc",
            "ouroboros.dev.runtime.kubernetes.mounted-workspace-root=/mnt/dev-runtime",
            "ouroboros.dev.runtime.kubernetes.helper-ready-timeout=45s",
            "ouroboros.dev.runtime.kubernetes.ingress-host-suffix=develop.example.com"
        )
        .withConfiguration(AutoConfigurations.of(KubernetesRuntimeAutoConfiguration.class))
        .run(context -> {
          assertEquals(1, context.getBeansOfType(PvcWorkspaceProvisioner.class).size());
          assertEquals(1, context.getBeansOfType(KubernetesRuntimeProperties.class).size());
          KubernetesRuntimeProperties properties = context.getBean(KubernetesRuntimeProperties.class);
          assertEquals("dev-space", properties.getNamespace());
          assertEquals("dev-runtime-pvc", properties.getSharedPvcName());
          assertEquals("/mnt/dev-runtime", properties.getMountedWorkspaceRoot());
          assertEquals(Duration.ofSeconds(45), properties.getHelperReadyTimeout());
          assertEquals("develop.example.com", properties.getIngressHostSuffix());
        });
  }

  static class FakeDevelopWorkspaceContext implements DevelopWorkspaceContext {
    @Override
    public String getRootDir() {
      return "/workspace";
    }

    @Override
    public String getDevRootDir() {
      return "/workspace/dev";
    }

    @Override
    public String getRunRootDir() {
      return "/workspace/run";
    }

    @Override
    public String getDebugSupportFilesDir() {
      return "/workspace/debug-support";
    }

    @Override
    public String getDevelopSupportFilesDir() {
      return "/workspace/develop-support";
    }
  }
}
