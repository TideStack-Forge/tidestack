package com.ouroboros.enterprise.manage;

import java.util.List;
import java.util.Set;
import com.ouroboros.integration.management.*;

/** Contributes the safe workbench entry generator; publishing remains a supplier-side operation in this phase. */
public final class EnterpriseWorkbenchIntegrationContributor implements IntegrationManagementContributor {
  static final String VIEW = EnterpriseWorkbenchManagementController.VIEW;
  @Override public List<IntegrationManagementPage> pages() {
    return List.of(new IntegrationManagementPage("enterprise-workbench", IntegrationManagementPage.Section.CONNECTIONS,
        "工作台入口", "/system-setting/integration-management/enterprise-workbench", 60, Set.of(VIEW)));
  }
}
