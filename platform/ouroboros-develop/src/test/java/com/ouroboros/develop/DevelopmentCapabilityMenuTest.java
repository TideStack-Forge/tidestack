package com.ouroboros.develop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.dev.app.model.DevMenuModel;
import com.ouroboros.dev.app.service.impl.DevMenuServiceImpl;
import com.ouroboros.uimodel.JarUIModelProvider;
import com.ouroboros.util.Trees;

import static org.assertj.core.api.Assertions.assertThat;

class DevelopmentCapabilityMenuTest {
  @BeforeEach
  void signInDeveloper() {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("developer", "unused"));
  }

  @AfterEach
  void clearDeveloper() {
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @CsvSource({
      "BasicConfiguration.Resource, resource",
      "OperationsMonitoring.ErrorCode, error-code"
  })
  void developerCanOpenBundledResourceAndErrorCodeEditors(String name, String path) {
    var menus = new DevMenuServiceImpl().getMainMenus("sample-app");
    assertThat(Trees.<DevMenuModel>flattenTree(menus, DevMenuModel::getChildren)).anySatisfy(menu -> {
      assertThat(menu.getFullName()).isEqualTo(name);
      assertThat(menu.getUrl()).isEqualTo("/dev/sample-app/" + path);
    });
    assertThat(new JarUIModelProvider().getUIModel("/dev/{appName}/" + path)).isPresent();
  }
}
