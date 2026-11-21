package com.ouroboros.ability.auth.config;

import java.util.List;
import java.util.Set;
import com.ouroboros.integration.management.IntegrationManagementContributor;
import com.ouroboros.integration.management.IntegrationManagementPage;
import com.ouroboros.integration.management.IntegrationManagementPage.Section;

public final class ExternalAuthIntegrationContributor implements IntegrationManagementContributor {
  @Override
  public List<IntegrationManagementPage> pages() {
    return List.of(page("oauth2", "通用 OAuth2 / OIDC", 40), page("ldap", "LDAP / AD", 50),
        page("cas", "CAS", 60), page("settings", "登录公共设置", 100));
  }
  private IntegrationManagementPage page(String group, String title, int order) {
    return new IntegrationManagementPage("external-auth-" + group, Section.CONNECTIONS, title,
        "/system-setting/integration-management/external-auth/" + group, order,
        Set.of(ExternalAuthManagementAccess.authority(group)));
  }
}
