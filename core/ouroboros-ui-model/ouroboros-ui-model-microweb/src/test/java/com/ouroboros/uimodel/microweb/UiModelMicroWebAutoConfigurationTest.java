package com.ouroboros.uimodel.microweb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

class UiModelMicroWebAutoConfigurationTest {

  @Test
  void resolveDeployPath_shouldPreferNonBlankEntry() {
    assertEquals(
        "/debug-console-header/",
        UiModelMicroWebAutoConfiguration.resolveDeployPath(microWeb("/unused-public-path/", "/debug-console-header/"))
    );
  }

  @Test
  void resolveDeployPath_shouldFallbackToPublicPathWhenEntryBlank() {
    assertEquals(
        "/micro-logicflow-editor/",
        UiModelMicroWebAutoConfiguration.resolveDeployPath(microWeb("/micro-logicflow-editor/", ""))
    );
  }

  @Test
  void resolveDeployPath_shouldReturnNullWhenModelMissing() {
    assertNull(UiModelMicroWebAutoConfiguration.resolveDeployPath(null));
  }

  private static MicroWebModel microWeb(String publicPath, String entry) {
    return new MicroWebModel() {
      @Override
      public String getName() {
        return "test";
      }

      @Override
      public MicroWebType getType() {
        return MicroWebType.APP;
      }

      @Override
      public String getEntry() {
        return entry;
      }

      @Override
      public String getPublicPath() {
        return publicPath;
      }

      @Override
      public InputStream getPackageContent() {
        return null;
      }
    };
  }
}
