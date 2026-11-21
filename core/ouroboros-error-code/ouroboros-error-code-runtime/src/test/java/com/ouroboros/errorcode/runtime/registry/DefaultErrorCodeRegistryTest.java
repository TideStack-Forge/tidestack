package com.ouroboros.errorcode.runtime.registry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.AppContext;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.provider.ErrorCodeDefinitionProvider;
import com.ouroboros.errorcode.runtime.provider.ResourceErrorCodeDefinitionProvider;
import com.ouroboros.errorcode.runtime.provider.WorkspaceErrorCodeDefinitionProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultErrorCodeRegistryTest {

  @TempDir
  Path tempDir;

  @Test
  void workspaceDefinitionsOverrideBundledDefaults() {
    var bundled = definition(40301, "登录已过期");
    var workspace = definition(40301, "请重新登录");
    var registry = new DefaultErrorCodeRegistry(Arrays.asList(
        provider(100, bundled),
        provider(0, workspace)
    ));

    assertEquals("请重新登录", registry.getDefinition(40301).get().getDefaultMessageTemplate());
  }

  @Test
  void disabledDefinitionsAreNotReturned() {
    var disabled = definition(50001, "内部错误").setEnabled(false);
    var registry = new DefaultErrorCodeRegistry(Collections.singletonList(provider(0, disabled)));

    assertFalse(registry.getDefinition(50001).isPresent());
    assertTrue(registry.getAllDefinitions().isEmpty());
  }

  @Test
  void duplicateDefinitionsWithinTheSameTierUseLaterDefinition() {
    var first = definition(41001, "库存不足");
    var second = definition(41001, "库存仍然不足");

    var registry = new DefaultErrorCodeRegistry(Arrays.asList(
        provider(0, first),
        provider(0, second)
    ));

    assertEquals("库存仍然不足", registry.getDefinition(41001).get().getDefaultMessageTemplate());
  }

  @Test
  void resourceProviderLoadsBundledStarterDefinitions() {
    var provider = new ResourceErrorCodeDefinitionProvider();

    assertTrue(provider.getDefinitions().stream().anyMatch(it -> Integer.valueOf(50000).equals(it.getCode())));
  }

  @Test
  void workspaceProviderLoadsWorkspaceDefinitions() throws Exception {
    Files.write(
        tempDir.resolve("error-codes.error.json"),
        Collections.singletonList("[{\"code\":40301,\"title\":\"Auth\",\"defaultMessageTemplate\":\"请重新登录\",\"enabled\":true}]"),
        StandardCharsets.UTF_8
    );

    var provider = new WorkspaceErrorCodeDefinitionProvider(appContext(tempDir));

    assertEquals(1, provider.getDefinitions().size());
    assertEquals("请重新登录", provider.getDefinitions().get(0).getDefaultMessageTemplate());
  }

  @Test
  void workspaceProviderIsANoopWhenWorkspacePathMissing() {
    var provider = new WorkspaceErrorCodeDefinitionProvider(new AppContext() {
      @Override
      public String getHomePath() {
        return null;
      }
    });

    assertTrue(provider.getDefinitions().isEmpty());
  }

  private static ErrorCodeDefinition definition(int code, String defaultMessageTemplate) {
    return new ErrorCodeDefinition()
        .setCode(code)
        .setTitle("CODE-" + code)
        .setDefaultMessageTemplate(defaultMessageTemplate)
        .setEnabled(true);
  }

  private static ErrorCodeDefinitionProvider provider(int order, ErrorCodeDefinition... definitions) {
    return new ErrorCodeDefinitionProvider() {
      @Override
      public int getOrder() {
        return order;
      }

      @Override
      public java.util.List<ErrorCodeDefinition> getDefinitions() {
        return Arrays.asList(definitions);
      }
    };
  }

  private static AppContext appContext(Path homePath) {
    return new AppContext() {
      @Override
      public String getHomePath() {
        return homePath.toString();
      }
    };
  }
}
