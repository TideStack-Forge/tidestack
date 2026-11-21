package com.ouroboros.ability.auth.oidc.management;

import java.util.List;
import java.util.Set;
import com.ouroboros.integration.management.IntegrationManagementContributor;
import com.ouroboros.integration.management.IntegrationManagementPage;
import com.ouroboros.integration.management.IntegrationManagementPage.Section;

public final class OidcIntegrationContributor implements IntegrationManagementContributor {
  @Override
  public List<IntegrationManagementPage> pages() {
    return List.of(new IntegrationManagementPage("auth-clients", Section.APPLICATIONS, "应用列表",
        "/system-setting/integration-management/applications/clients", 10,
        Set.of(ClientManagementAccess.SECURITY, ClientManagementAccess.MANAGE)),
        new IntegrationManagementPage("auth-server", Section.APPLICATIONS, "授权服务器",
            "/system-setting/integration-management/applications/settings", 20, Set.of(ClientManagementAccess.SETTINGS)),
        new IntegrationManagementPage("dynamic-registration", Section.APPLICATIONS, "动态注册 DCR",
            "/system-setting/integration-management/applications/registration", 30, Set.of(ClientManagementAccess.SECURITY)));
  }
}
