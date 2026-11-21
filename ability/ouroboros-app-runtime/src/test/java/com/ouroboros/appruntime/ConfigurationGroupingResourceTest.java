package com.ouroboros.appruntime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.configuration.metadata.ConfigValueType;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadata;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadataProvider;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadataProvider;
import com.ouroboros.configuration.provider.JarConfigurationGroupMetadataProvider;
import com.ouroboros.configuration.provider.JarConfigurationMetadataProvider;

public class ConfigurationGroupingResourceTest {

  private final ConfigurationGroupMetadataProvider groupMetadataProvider = new JarConfigurationGroupMetadataProvider();
  private final ConfigurationMetadataProvider metadataProvider = new JarConfigurationMetadataProvider();

  @Test
  public void appConfigurationResources_exposeParsedGroupAndMetadataContracts() {
    List<ConfigurationGroupMetadata> groups = groupMetadataProvider.getAllConfigurationGroupMetadata();
    List<ConfigurationMetadata> items = metadataProvider.getAllConfigurationMetadata();

    assertGroup(groups, "app", "应用设置", "desktop", Integer.valueOf(30), "应用运行时展示与主题配置");
    assertGroup(groups, "ui", "界面设置", "desktop", Integer.valueOf(30), "界面与标签页展示配置");
    assertGroup(groups, "ui.sidebar", "侧边栏设置", "columns", Integer.valueOf(20), "主壳侧边栏默认宽度与拖拽配置");
    assertGroup(groups, "ui.tabs", "标签页设置", "window-maximize", Integer.valueOf(30), "标签页展示与图标配置");
    assertGroup(groups, "ui.crud", "CRUD 设置", "table", Integer.valueOf(40), "CRUD 列表与筛选区展示配置");
    assertGroup(groups, "ui.crud.filter", "筛选区设置", "filter", Integer.valueOf(10), "CRUD 筛选区布局与交互配置");
    assertGroup(groups, "ui.crud.filter.smartLayout", "筛选区智能布局", "th-large", Integer.valueOf(10),
        "CRUD 筛选项自动排版、标签与每行字段数配置");
    assertGroup(groups, "ui.form", "表单设置", "wpforms", Integer.valueOf(50), "普通表单布局与交互配置");
    assertGroup(groups, "ui.form.smartLayout", "表单智能布局", "th-large", Integer.valueOf(10),
        "普通表单自动排版、标签与列数配置");
    assertGroup(groups, "app.branding", "品牌信息", "id-card", Integer.valueOf(10), "应用名称与品牌标识");
    assertGroup(groups, "app.theme", "主题设置", "paint-brush", Integer.valueOf(20), "应用默认主题与锁定策略");

    ConfigurationMetadata appName = findMetadata(items, "appName");
    assertEquals("应用名称", appName.getLabel());
    assertEquals("app.branding", appName.getGroupPath());
    assertEquals(Integer.valueOf(10), appName.getOrder());
    assertEquals(ConfigValueType.STRING, appName.getValueType());
    assertTrue(appName.isModifiable());
    assertFalse(appName.isNullable());
    assertEquals("新应用", appName.getDefaultValue());

    ConfigurationMetadata logo = findMetadata(items, "logo");
    assertEquals("应用Logo", logo.getLabel());
    assertEquals("app.branding", logo.getGroupPath());
    assertEquals(Integer.valueOf(20), logo.getOrder());
    assertEquals(ConfigValueType.STRING, logo.getValueType());
    assertTrue(logo.isModifiable());
    assertTrue(logo.isNullable());
    assertNull(logo.getDefaultValue());

    ConfigurationMetadata defaultTheme = findMetadata(items, "defaultTheme");
    assertEquals("默认主题", defaultTheme.getLabel());
    assertEquals("app.theme", defaultTheme.getGroupPath());
    assertEquals(Integer.valueOf(10), defaultTheme.getOrder());
    assertEquals(ConfigValueType.STRING, defaultTheme.getValueType());
    assertTrue(defaultTheme.isModifiable());
    assertFalse(defaultTheme.isNullable());
    assertEquals("dark", defaultTheme.getDefaultValue());
    assertNotNull(defaultTheme.getEditorSchema());
    assertEquals("button-group-select", defaultTheme.getEditorSchema().getType());

    ConfigurationMetadata isLockTheme = findMetadata(items, "isLockTheme");
    assertEquals("是否锁定主题", isLockTheme.getLabel());
    assertEquals("app.theme", isLockTheme.getGroupPath());
    assertEquals(Integer.valueOf(20), isLockTheme.getOrder());
    assertEquals(ConfigValueType.BOOLEAN, isLockTheme.getValueType());
    assertTrue(isLockTheme.isModifiable());
    assertFalse(isLockTheme.isNullable());
    assertEquals("false", isLockTheme.getDefaultValue());

    ConfigurationMetadata tabsIcon = findMetadata(items, "ui.tabs.icon.enabled");
    assertEquals("是否显示标签页图标", tabsIcon.getLabel());
    assertEquals("ui.tabs", tabsIcon.getGroupPath());
    assertEquals(Integer.valueOf(10), tabsIcon.getOrder());
    assertEquals("是否在标签页标题旁显示图标", tabsIcon.getDescription());
    assertEquals(ConfigValueType.BOOLEAN, tabsIcon.getValueType());
    assertTrue(tabsIcon.isModifiable());
    assertFalse(tabsIcon.isNullable());
    assertEquals("true", tabsIcon.getDefaultValue());

    ConfigurationMetadata smartLayoutEnabled = findMetadata(items, "ui.crud.filter.smartLayout.enabled");
    assertEquals("启用筛选区智能布局", smartLayoutEnabled.getLabel());
    assertEquals("ui.crud.filter.smartLayout", smartLayoutEnabled.getGroupPath());
    assertEquals(Integer.valueOf(10), smartLayoutEnabled.getOrder());
    assertEquals("控制 CRUD 筛选区是否按字段类型和容器宽度自动排版", smartLayoutEnabled.getDescription());
    assertEquals(ConfigScope.FRONTEND, smartLayoutEnabled.getScope());
    assertEquals(ConfigValueType.BOOLEAN, smartLayoutEnabled.getValueType());
    assertTrue(smartLayoutEnabled.isModifiable());
    assertFalse(smartLayoutEnabled.isNullable());
    assertEquals("true", smartLayoutEnabled.getDefaultValue());

    ConfigurationMetadata labelStyle = findMetadata(items, "ui.crud.filter.smartLayout.labelStyle");
    assertEquals("筛选项标签风格", labelStyle.getLabel());
    assertEquals("ui.crud.filter.smartLayout", labelStyle.getGroupPath());
    assertEquals(Integer.valueOf(20), labelStyle.getOrder());
    assertEquals(ConfigScope.FRONTEND, labelStyle.getScope());
    assertEquals(ConfigValueType.STRING, labelStyle.getValueType());
    assertTrue(labelStyle.isModifiable());
    assertFalse(labelStyle.isNullable());
    assertEquals("label", labelStyle.getDefaultValue());
    assertNotNull(labelStyle.getEditorSchema());
    assertEquals("button-group-select", labelStyle.getEditorSchema().getType());

    assertFieldsPerRowMetadata(items, "ui.crud.filter.smartLayout.fieldsPerRow.desktop", "宽屏每行字段数",
        Integer.valueOf(30), "5");
    assertFieldsPerRowMetadata(items, "ui.crud.filter.smartLayout.fieldsPerRow.narrow", "窄屏每行字段数",
        Integer.valueOf(40), "4");
    assertFieldsPerRowMetadata(items, "ui.crud.filter.smartLayout.fieldsPerRow.mobile", "移动端每行字段数",
        Integer.valueOf(50), "1");

    ConfigurationMetadata formSmartLayoutEnabled = findMetadata(items, "ui.form.smartLayout.enabled");
    assertEquals("启用表单智能布局", formSmartLayoutEnabled.getLabel());
    assertEquals("ui.form.smartLayout", formSmartLayoutEnabled.getGroupPath());
    assertEquals(Integer.valueOf(10), formSmartLayoutEnabled.getOrder());
    assertEquals("控制普通表单在未显式声明 smartLayout 时是否自动启用智能布局", formSmartLayoutEnabled.getDescription());
    assertEquals(ConfigScope.FRONTEND, formSmartLayoutEnabled.getScope());
    assertEquals(ConfigValueType.BOOLEAN, formSmartLayoutEnabled.getValueType());
    assertTrue(formSmartLayoutEnabled.isModifiable());
    assertFalse(formSmartLayoutEnabled.isNullable());
    assertEquals("false", formSmartLayoutEnabled.getDefaultValue());

    ConfigurationMetadata formLabelStyle = findMetadata(items, "ui.form.smartLayout.labelStyle");
    assertEquals("表单标签风格", formLabelStyle.getLabel());
    assertEquals("ui.form.smartLayout", formLabelStyle.getGroupPath());
    assertEquals(Integer.valueOf(20), formLabelStyle.getOrder());
    assertEquals(ConfigScope.FRONTEND, formLabelStyle.getScope());
    assertEquals(ConfigValueType.STRING, formLabelStyle.getValueType());
    assertTrue(formLabelStyle.isModifiable());
    assertFalse(formLabelStyle.isNullable());
    assertEquals("normal", formLabelStyle.getDefaultValue());
    assertNotNull(formLabelStyle.getEditorSchema());
    assertEquals("button-group-select", formLabelStyle.getEditorSchema().getType());

    assertFormSmartLayoutIntMetadata(items, "ui.form.smartLayout.smallItemLimit", "单列字段数阈值",
        Integer.valueOf(30), "5");
    assertFormSmartLayoutIntMetadata(items, "ui.form.smartLayout.columnCount.small", "小表单列数",
        Integer.valueOf(40), "1");
    assertFormSmartLayoutIntMetadata(items, "ui.form.smartLayout.columnCount.large", "大表单列数",
        Integer.valueOf(50), "2");

    ConfigurationMetadata sidebarWidth = findMetadata(items, "sidebar.width");
    assertEquals("侧边栏默认宽度", sidebarWidth.getLabel());
    assertEquals("ui.sidebar", sidebarWidth.getGroupPath());
    assertEquals(Integer.valueOf(10), sidebarWidth.getOrder());
    assertEquals("侧边栏展开态默认宽度（像素）", sidebarWidth.getDescription());
    assertEquals(ConfigValueType.INT, sidebarWidth.getValueType());
    assertTrue(sidebarWidth.isModifiable());
    assertFalse(sidebarWidth.isNullable());
    assertEquals("200", sidebarWidth.getDefaultValue());

    ConfigurationMetadata sidebarMinWidth = findMetadata(items, "sidebar.minWidth");
    assertEquals("侧边栏最小宽度", sidebarMinWidth.getLabel());
    assertEquals("ui.sidebar", sidebarMinWidth.getGroupPath());
    assertEquals(Integer.valueOf(20), sidebarMinWidth.getOrder());
    assertEquals("侧边栏拖拽允许的最小展开宽度（像素）", sidebarMinWidth.getDescription());
    assertEquals(ConfigValueType.INT, sidebarMinWidth.getValueType());
    assertTrue(sidebarMinWidth.isModifiable());
    assertFalse(sidebarMinWidth.isNullable());
    assertEquals("160", sidebarMinWidth.getDefaultValue());

    ConfigurationMetadata sidebarMaxWidth = findMetadata(items, "sidebar.maxWidth");
    assertEquals("侧边栏最大宽度", sidebarMaxWidth.getLabel());
    assertEquals("ui.sidebar", sidebarMaxWidth.getGroupPath());
    assertEquals(Integer.valueOf(30), sidebarMaxWidth.getOrder());
    assertEquals("侧边栏拖拽允许的最大展开宽度（像素）", sidebarMaxWidth.getDescription());
    assertEquals(ConfigValueType.INT, sidebarMaxWidth.getValueType());
    assertTrue(sidebarMaxWidth.isModifiable());
    assertFalse(sidebarMaxWidth.isNullable());
    assertEquals("360", sidebarMaxWidth.getDefaultValue());

    ConfigurationMetadata sidebarResizable = findMetadata(items, "sidebar.resizable");
    assertEquals("是否允许拖拽调宽", sidebarResizable.getLabel());
    assertEquals("ui.sidebar", sidebarResizable.getGroupPath());
    assertEquals(Integer.valueOf(40), sidebarResizable.getOrder());
    assertEquals("控制主壳侧边栏是否渲染拖拽调宽手柄", sidebarResizable.getDescription());
    assertEquals(ConfigValueType.BOOLEAN, sidebarResizable.getValueType());
    assertTrue(sidebarResizable.isModifiable());
    assertFalse(sidebarResizable.isNullable());
    assertEquals("true", sidebarResizable.getDefaultValue());
  }

  private void assertFieldsPerRowMetadata(List<ConfigurationMetadata> items, String key, String label,
      Integer order, String defaultValue) {
    ConfigurationMetadata metadata = findMetadata(items, key);
    assertEquals(label, metadata.getLabel());
    assertEquals("ui.crud.filter.smartLayout", metadata.getGroupPath());
    assertEquals(order, metadata.getOrder());
    assertEquals(ConfigScope.FRONTEND, metadata.getScope());
    assertEquals(ConfigValueType.INT, metadata.getValueType());
    assertTrue(metadata.isModifiable());
    assertFalse(metadata.isNullable());
    assertEquals(defaultValue, metadata.getDefaultValue());
    assertEquals(Integer.valueOf(1), metadata.getExtraProp("min").get());
  }

  private void assertFormSmartLayoutIntMetadata(List<ConfigurationMetadata> items, String key, String label,
      Integer order, String defaultValue) {
    ConfigurationMetadata metadata = findMetadata(items, key);
    assertEquals(label, metadata.getLabel());
    assertEquals("ui.form.smartLayout", metadata.getGroupPath());
    assertEquals(order, metadata.getOrder());
    assertEquals(ConfigScope.FRONTEND, metadata.getScope());
    assertEquals(ConfigValueType.INT, metadata.getValueType());
    assertTrue(metadata.isModifiable());
    assertFalse(metadata.isNullable());
    assertEquals(defaultValue, metadata.getDefaultValue());
    assertEquals(Integer.valueOf(1), metadata.getExtraProp("min").get());
  }

  private void assertGroup(List<ConfigurationGroupMetadata> groups, String path, String title, String icon,
      Integer order, String description) {
    ConfigurationGroupMetadata group = findGroup(groups, path);
    assertEquals(title, group.getTitle());
    assertEquals(icon, group.getIcon());
    assertEquals(order, group.getOrder());
    assertEquals(description, group.getDescription());
  }

  private ConfigurationGroupMetadata findGroup(List<ConfigurationGroupMetadata> groups, String path) {
    for (ConfigurationGroupMetadata group : groups) {
      if (path.equals(group.getPath())) {
        return group;
      }
    }

    fail("Missing configuration group metadata for path: " + path);
    return null;
  }

  private ConfigurationMetadata findMetadata(List<ConfigurationMetadata> items, String key) {
    for (ConfigurationMetadata item : items) {
      if (key.equals(item.getKey())) {
        return item;
      }
    }

    fail("Missing configuration metadata for key: " + key);
    return null;
  }
}
