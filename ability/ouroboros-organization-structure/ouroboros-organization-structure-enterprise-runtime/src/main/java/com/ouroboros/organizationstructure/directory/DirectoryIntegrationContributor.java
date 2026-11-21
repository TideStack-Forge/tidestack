package com.ouroboros.organizationstructure.directory;

import java.util.List;
import java.util.Set;
import com.ouroboros.integration.management.*;

public final class DirectoryIntegrationContributor implements IntegrationManagementContributor {
  @Override public List<IntegrationManagementPage> pages() {
    return List.of(new IntegrationManagementPage("enterprise-directory", IntegrationManagementPage.Section.CONNECTIONS,
        "组织关联与同步", "/system-setting/integration-management/enterprise-directory", 40,
        Set.of(EnterpriseDirectoryController.VIEW)));
  }
}
