package com.ouroboros.integration.management;

import java.util.Set;

/** Presentation references only; values and business capabilities remain with their owners. */
public record IntegrationManagementPage(String id, Section section, String title, String schemaPath,
    int order, Set<String> authorities) {
  public IntegrationManagementPage {
    if (id == null || id.isBlank() || section == null || title == null || title.isBlank()
        || schemaPath == null || !schemaPath.startsWith("/") || authorities == null || authorities.isEmpty()) {
      throw new IllegalArgumentException("集成管理页面须声明标识、分区、标题、Schema 路径及权限");
    }
    authorities = Set.copyOf(authorities);
  }

  public enum Section { CONNECTIONS, APPLICATIONS }
}
