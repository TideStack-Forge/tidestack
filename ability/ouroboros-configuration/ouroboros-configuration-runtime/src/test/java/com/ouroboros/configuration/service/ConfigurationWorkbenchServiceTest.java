package com.ouroboros.configuration.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.ouroboros.Application;
import com.ouroboros.configuration.ConfigurationRevision;
import com.ouroboros.configuration.service.impl.ConfigurationWorkbenchServiceImpl;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Application.class})
public class ConfigurationWorkbenchServiceTest {

  @Autowired
  private ConfigurationWorkbenchService workbenchService;

  @Test
  public void getTree_buildsGroupedNavigationWithoutItemNodesAndUngroupedFallback() {
    var tree = workbenchService.getTree();
    List<String> topLevelPaths = tree.stream()
        .map(node -> node.getPath() == null ? "" : node.getPath())
        .collect(Collectors.toList());

    assertTrue(topLevelPaths.contains("security"));
    assertTrue(topLevelPaths.contains(ConfigurationWorkbenchServiceImpl.UNGROUPED_PATH));
    assertEquals("其他设置", tree.stream()
        .filter(node -> ConfigurationWorkbenchServiceImpl.UNGROUPED_PATH.equals(node.getPath()))
        .findFirst()
        .get()
        .getLabel());

    var securityNode = tree.stream()
        .filter(node -> "security".equals(node.getPath()))
        .findFirst()
        .get();
    List<String> childPaths = securityNode.getChildren().stream()
        .map(node -> node.getPath() == null ? "" : node.getPath())
        .collect(Collectors.toList());
    assertTrue(childPaths.contains("security.session"));
    assertTrue(childPaths.contains("security.auth"));
    assertTrue(securityNode.getChildren().stream().allMatch(node -> ConfigurationWorkbenchServiceImpl.GROUP_TYPE.equals(node.getType())));
    assertTrue(tree.stream().allMatch(node -> node.getIcon() != null && !node.getIcon().trim().isEmpty()));
  }

  @Test
  public void getGroupPanel_returnsChildGroupsAndDirectItems() {
    var panel = workbenchService.getGroupPanel("security").get();
    assertEquals("安全设置", panel.getTitle());
    assertEquals(2, panel.getChildGroups().size());
    assertEquals(Integer.valueOf(3), panel.getStatistics().getTotalItems());

    var sessionGroup = panel.getChildGroups().stream()
        .filter(group -> "security.session".equals(group.getPath()))
        .findFirst()
        .get();
    assertFalse(sessionGroup.getItems().isEmpty());
    assertTrue(sessionGroup.getItems().stream().anyMatch(item -> "test0".equals(item.getKey())));
    assertTrue(sessionGroup.getChildGroups().isEmpty());
    assertTrue(sessionGroup.getIcon() != null && sessionGroup.getIcon().startsWith("fa fa-"));

    var ungroupedPanel = workbenchService.getGroupPanel(ConfigurationWorkbenchServiceImpl.UNGROUPED_PATH).get();
    assertEquals(1, ungroupedPanel.getItems().size());
    assertEquals("loose0", ungroupedPanel.getItems().get(0).getKey());
  }

  @Test
  public void getItemPanel_returnsBreadcrumbsAndEditorSchema() {
    var panel = workbenchService.getItemPanel("test0").get();
    assertEquals("Test 0", panel.getLabel());
    assertEquals("security.session", panel.getGroupPath());
    assertEquals(ConfigurationRevision.defaultRevision("test0", "test0-default"), panel.getRevision());
    assertEquals("input-text", panel.getEditorSchema().getType());

    List<String> labels = panel.getBreadcrumbs().stream()
        .map(breadcrumb -> breadcrumb.getLabel())
        .collect(Collectors.toList());
    assertEquals("全部配置", labels.get(0));
    assertEquals("安全设置", labels.get(1));
    assertEquals("会话管理", labels.get(2));
    assertEquals("Test 0", labels.get(3));
  }
}
