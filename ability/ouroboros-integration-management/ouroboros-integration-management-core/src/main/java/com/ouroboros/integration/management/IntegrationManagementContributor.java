package com.ouroboros.integration.management;

import java.util.List;

/** Management adapters contribute static references through their registered AutoConfiguration. */
public interface IntegrationManagementContributor {
  List<IntegrationManagementPage> pages();
}
