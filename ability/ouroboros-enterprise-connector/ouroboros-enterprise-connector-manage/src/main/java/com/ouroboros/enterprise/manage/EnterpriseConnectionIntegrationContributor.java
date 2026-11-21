package com.ouroboros.enterprise.manage;

import java.util.List;
import java.util.Set;

import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.enterprise.runtime.management.EnterpriseConnectionAccess;
import com.ouroboros.integration.management.IntegrationManagementContributor;
import com.ouroboros.integration.management.IntegrationManagementPage;

/** Only installed single-enterprise provider contracts contribute management tabs. */
public final class EnterpriseConnectionIntegrationContributor implements IntegrationManagementContributor {
  static final String PATH = "/system-setting/integration-management/enterprise/";
  private final EnterpriseConnectionProviderRegistry providers;

  public EnterpriseConnectionIntegrationContributor(EnterpriseConnectionProviderRegistry providers) {
    this.providers = providers;
  }

  @Override
  public List<IntegrationManagementPage> pages() {
    return providers.providers().stream().filter(provider -> provider.maxConnections() == 1)
        .map(provider -> new IntegrationManagementPage("enterprise-" + provider.provider(),
            IntegrationManagementPage.Section.CONNECTIONS, provider.displayName(),
            PATH + provider.provider(), 10, Set.of(EnterpriseConnectionAccess.VIEW))).toList();
  }

}
