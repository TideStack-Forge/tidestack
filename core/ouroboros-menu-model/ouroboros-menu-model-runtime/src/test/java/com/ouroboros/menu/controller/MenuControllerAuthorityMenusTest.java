package com.ouroboros.menu.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelShowModeEnum;
import com.ouroboros.menu.service.MenuService;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class MenuControllerAuthorityMenusTest {

  @AfterEach
  void clearSecurityContext() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void authorityMenusShouldPreferCurrentSecurityContextIdentity() {
    installConflictingSecurityIdentities();

    List<TestMenuModel> mainMenus = Collections.singletonList(new TestMenuModel("main.dashboard", "/main/dashboard", "主菜单"));
    List<TestMenuModel> secondaryMenus = Collections.singletonList(new TestMenuModel("secondary.dashboard", "/secondary/dashboard", "次级菜单"));
    MenuService menuService = new RecordingMenuService("seam-user", mainMenus, secondaryMenus);

    MenuController controller = new MenuController();
    controller.menuService = menuService;

    WebApiResult<?> result = controller.authorityMenus();

    assertEquals(Integer.valueOf(0), result.getCode());
    Map<?, ?> menuTreeMap = assertInstanceOf(Map.class, result.getData());
    assertSame(mainMenus, menuTreeMap.get("main"));
    assertSame(secondaryMenus, menuTreeMap.get("secondary"));
  }

  @Test
  void authorityMenusShouldRejectLegacyPrincipalWhenSeamContextMissing() {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.empty());
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("legacy-holder-user", null));

    List<TestMenuModel> mainMenus = Collections.singletonList(new TestMenuModel("main.dashboard", "/main/dashboard", "主菜单"));
    List<TestMenuModel> secondaryMenus = Collections.singletonList(new TestMenuModel("secondary.dashboard", "/secondary/dashboard", "次级菜单"));
    MenuService menuService = new RecordingMenuService(null, mainMenus, secondaryMenus);

    MenuController controller = new MenuController();
    controller.menuService = menuService;

    WebApiResult<?> result = controller.authorityMenus();

    assertEquals(Integer.valueOf(0), result.getCode());
    Map<?, ?> menuTreeMap = assertInstanceOf(Map.class, result.getData());
    assertSame(mainMenus, menuTreeMap.get("main"));
    assertSame(secondaryMenus, menuTreeMap.get("secondary"));
  }

  private void installConflictingSecurityIdentities() {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> "seam-user")));
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("legacy-holder-user", null));
  }

  private static final class TestMenuModel implements MenuModel {

    private final String fullName;
    private final String url;
    private final String title;

    private TestMenuModel(String fullName, String url, String title) {
      this.fullName = fullName;
      this.url = url;
      this.title = title;
    }

    @Override
    public String getUrl() {
      return url;
    }

    @Override
    public String getFullName() {
      return fullName;
    }

    @Override
    public String getIcon() {
      return null;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public MenuModelShowModeEnum getShowMode() {
      return null;
    }

    @Override
    public Integer getOrder() {
      return 0;
    }
  }

  private static final class RecordingMenuService implements MenuService {

    private final String expectedUserIdentity;
    private final List<? extends MenuModel> mainMenus;
    private final List<? extends MenuModel> secondaryMenus;

    private RecordingMenuService(String expectedUserIdentity,
                                 List<? extends MenuModel> mainMenus,
                                 List<? extends MenuModel> secondaryMenus) {
      this.expectedUserIdentity = expectedUserIdentity;
      this.mainMenus = mainMenus;
      this.secondaryMenus = secondaryMenus;
    }

    @Override
    public List<? extends MenuModel> getMainMenus(String userIdentity) {
      assertExpectedUserIdentity(userIdentity);
      return mainMenus;
    }

    @Override
    public List<? extends MenuModel> getSecondaryMenus(String userIdentity) {
      assertExpectedUserIdentity(userIdentity);
      return secondaryMenus;
    }

    private void assertExpectedUserIdentity(String userIdentity) {
      if (!Objects.equals(expectedUserIdentity, userIdentity)) {
        throw new AssertionError("expected user identity " + expectedUserIdentity + " but got " + userIdentity);
      }
    }
  }
}
