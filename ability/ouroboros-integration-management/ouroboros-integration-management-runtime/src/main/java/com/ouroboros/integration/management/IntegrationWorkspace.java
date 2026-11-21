package com.ouroboros.integration.management;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.ouroboros.exception.InternalException;
import com.ouroboros.integration.management.IntegrationManagementPage.Section;

public final class IntegrationWorkspace {
  private final List<IntegrationManagementPage> pages;

  public IntegrationWorkspace(List<IntegrationManagementContributor> contributors) {
    pages = contributors.stream().flatMap(contributor -> contributor.pages().stream())
        .sorted(Comparator.comparingInt(IntegrationManagementPage::order).thenComparing(IntegrationManagementPage::id)).toList();
    var ids = new HashSet<String>();
    var paths = new HashSet<String>();
    for (var page : pages) {
      if (!ids.add(page.id()) || !paths.add(page.schemaPath())) {
        throw new InternalException("集成管理页面标识或编辑位置重复: " + page.id());
      }
    }
  }

  public Map<String, Object> schema(Section section, Predicate<String> permitted) {
    var tabs = pages.stream().filter(page -> page.section() == section)
        .filter(page -> page.authorities().stream().anyMatch(permitted))
        .map(page -> Map.<String, Object>of("title", page.title(), "hash", page.id(),
            "mountOnEnter", true, "body", Map.of("type", "service", "schemaApi",
                Map.of("method", "get", "url", "/api/ui-schema", "data", Map.of("path", page.schemaPath())))))
        .toList();
    if (tabs.isEmpty()) {
      return Map.of("type", "tpl", "tpl", "未安装可用的管理模块，或当前账号没有此区域的管理权限。");
    }
    return Map.of("type", "tabs", "tabs", tabs);
  }
}
