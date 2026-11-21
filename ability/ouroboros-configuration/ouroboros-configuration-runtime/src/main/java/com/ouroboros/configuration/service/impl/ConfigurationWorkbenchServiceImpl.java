package com.ouroboros.configuration.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.configuration.center.ConfigurationGroupMetadataCenter;
import com.ouroboros.configuration.metadata.ConfigurationGroupMetadata;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.configuration.model.ConfigurationModel;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchBreadcrumb;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchGroupPanel;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchGroupSummary;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchItemPanel;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchItemSummary;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchStatistics;
import com.ouroboros.configuration.model.workbench.ConfigurationWorkbenchTreeNode;
import com.ouroboros.configuration.service.ConfigurationWorkbenchService;
import com.ouroboros.configuration.service.RuntimeConfigurationService;
import com.ouroboros.configuration.util.ConfigurationEditorSchemaBuilder;

@Service
public class ConfigurationWorkbenchServiceImpl implements ConfigurationWorkbenchService {

  public static final String ROOT_PATH = "";
  public static final String ROOT_TITLE = "全部配置";
  public static final String ROOT_TYPE = "group";
  public static final String ITEM_TYPE = "item";
  public static final String GROUP_TYPE = "group";
  public static final String UNGROUPED_PATH = "__ungrouped__";
  public static final String UNGROUPED_TITLE = "其他设置";
  public static final String DEFAULT_GROUP_ICON = "fa fa-folder-open-o";

  @Autowired
  private RuntimeConfigurationService runtimeConfigurationService;

  @Autowired
  private ConfigurationEditorSchemaBuilder schemaBuilder;

  @Override
  public List<ConfigurationWorkbenchTreeNode> getTree() {
    WorkbenchSnapshot snapshot = snapshot();
    return buildChildren(snapshot, ROOT_PATH);
  }

  @Override
  public Optional<ConfigurationWorkbenchGroupPanel> getGroupPanel(String path) {
    WorkbenchSnapshot snapshot = snapshot();
    String normalizedPath = normalizeRequestedPath(path);
    if (!ROOT_PATH.equals(normalizedPath) && !snapshot.groupMetadataMap.containsKey(normalizedPath)) {
      return Optional.empty();
    }
    return Optional.of(buildGroupPanel(snapshot, normalizedPath));
  }

  @Override
  public Optional<ConfigurationWorkbenchItemPanel> getItemPanel(String key) {
    WorkbenchSnapshot snapshot = snapshot();
    ConfigurationModel model = snapshot.itemMap.get(key);
    if (model == null) {
      return Optional.empty();
    }

    ConfigurationWorkbenchItemSummary summary = toItemSummary(model);
    ConfigurationWorkbenchItemPanel panel = new ConfigurationWorkbenchItemPanel();
    panel.setKey(summary.getKey());
    panel.setLabel(summary.getLabel());
    panel.setGroupPath(summary.getGroupPath());
    panel.setValue(summary.getValue());
    panel.setRevision(summary.getRevision());
    panel.setEditorSchema(summary.getEditorSchema());
    panel.setMetadata(summary.getMetadata());
    panel.setConfigValue(summary.getConfigValue());
    panel.setBreadcrumbs(buildItemBreadcrumbs(snapshot, model));
    return Optional.of(panel);
  }

  private WorkbenchSnapshot snapshot() {
    Map<String, ConfigurationModel> itemMap = new TreeMap<>(String::compareToIgnoreCase);
    runtimeConfigurationService.getManagedConfigurationModels().forEach((key, model) -> {
      if (model.getMetadata() == null || !model.getMetadata().isManagementTakenOver()) {
        itemMap.put(key, model);
      }
    });

    Map<String, ConfigurationGroupMetadata> declaredGroupMap = new TreeMap<>(String::compareToIgnoreCase);
    declaredGroupMap.putAll(ConfigurationGroupMetadataCenter.getConfigurationGroupMetadataMap());

    TreeSet<String> groupPaths = new TreeSet<>(String::compareToIgnoreCase);
    declaredGroupMap.keySet().stream()
        .map(this::normalizeGroupPath)
        .filter(Objects::nonNull)
        .forEach(path -> addPathWithAncestors(groupPaths, path));

    boolean hasUngrouped = false;
    for (ConfigurationModel model : itemMap.values()) {
      String groupPath = resolveEffectiveGroupPath(model.getMetadata());
      if (UNGROUPED_PATH.equals(groupPath)) {
        hasUngrouped = true;
      } else {
        addPathWithAncestors(groupPaths, groupPath);
      }
    }
    if (hasUngrouped) {
      groupPaths.add(UNGROUPED_PATH);
    }

    Map<String, ConfigurationGroupMetadata> effectiveGroupMap = new TreeMap<>(String::compareToIgnoreCase);
    for (String path : groupPaths) {
      effectiveGroupMap.put(path, buildEffectiveGroupMetadata(path, declaredGroupMap.get(path)));
    }

    Map<String, List<String>> childGroupPathsByParent = new TreeMap<>(String::compareToIgnoreCase);
    for (String groupPath : groupPaths) {
      String parentPath = getParentPath(groupPath);
      childGroupPathsByParent.computeIfAbsent(parentPath, key -> new ArrayList<>()).add(groupPath);
    }

    Map<String, List<ConfigurationModel>> directItemsByGroup = new TreeMap<>(String::compareToIgnoreCase);
    for (ConfigurationModel model : itemMap.values()) {
      String groupPath = resolveEffectiveGroupPath(model.getMetadata());
      directItemsByGroup.computeIfAbsent(groupPath, key -> new ArrayList<>()).add(model);
    }

    return new WorkbenchSnapshot(itemMap, effectiveGroupMap, childGroupPathsByParent, directItemsByGroup);
  }

  private List<ConfigurationWorkbenchTreeNode> buildChildren(WorkbenchSnapshot snapshot, String parentPath) {
    List<ConfigurationWorkbenchTreeNode> result = new ArrayList<>();

    List<String> childGroupPaths = new ArrayList<>(snapshot.childGroupPathsByParent.getOrDefault(parentPath, Collections.emptyList()));
    childGroupPaths.stream()
        .map(path -> buildGroupTreeNode(snapshot, path))
        .sorted(groupNodeComparator())
        .forEach(result::add);

    return result;
  }

  private ConfigurationWorkbenchTreeNode buildGroupTreeNode(WorkbenchSnapshot snapshot, String path) {
    ConfigurationGroupMetadata metadata = snapshot.groupMetadataMap.get(path);
    ConfigurationWorkbenchTreeNode node = new ConfigurationWorkbenchTreeNode();
    node.setId("group:" + path);
    node.setType(GROUP_TYPE);
    node.setPath(path);
    node.setLabel(getGroupTitle(metadata));
    node.setIcon(resolveGroupIcon(metadata));
    node.setDescription(metadata.getDescription());
    node.setOrder(metadata.getOrder());
    List<ConfigurationWorkbenchTreeNode> children = buildChildren(snapshot, path);
    node.setChildren(children);
    node.setHasChildren(!children.isEmpty());
    return node;
  }

  private ConfigurationWorkbenchGroupPanel buildGroupPanel(WorkbenchSnapshot snapshot, String path) {
    ConfigurationGroupMetadata metadata = ROOT_PATH.equals(path)
        ? buildRootGroupMetadata()
        : snapshot.groupMetadataMap.get(path);

    ConfigurationWorkbenchGroupPanel panel = new ConfigurationWorkbenchGroupPanel();
    panel.setPath(path);
    panel.setTitle(getGroupTitle(metadata));
    panel.setIcon(resolveGroupIcon(metadata));
    panel.setDescription(metadata.getDescription());
    panel.setVirtualGroup(isVirtualGroup(path, metadata));
    panel.setBreadcrumbs(buildGroupBreadcrumbs(snapshot, path));

    List<String> childGroupPaths = new ArrayList<>(snapshot.childGroupPathsByParent.getOrDefault(path, Collections.emptyList()));
    panel.setChildGroups(childGroupPaths.stream()
        .map(childPath -> toGroupSummary(snapshot, childPath))
        .sorted(groupSummaryComparator())
        .collect(Collectors.toList()));

    List<ConfigurationModel> items = new ArrayList<>(snapshot.directItemsByGroup.getOrDefault(path, Collections.emptyList()));
    panel.setItems(items.stream()
        .sorted(itemComparator())
        .map(this::toItemSummary)
        .collect(Collectors.toList()));
    panel.setStatistics(buildStatistics(snapshot, path));
    return panel;
  }

  private ConfigurationWorkbenchGroupSummary toGroupSummary(WorkbenchSnapshot snapshot, String path) {
    ConfigurationGroupMetadata metadata = snapshot.groupMetadataMap.get(path);
    ConfigurationWorkbenchStatistics statistics = buildStatistics(snapshot, path);
    ConfigurationWorkbenchGroupSummary summary = new ConfigurationWorkbenchGroupSummary();
    summary.setPath(path);
    summary.setTitle(getGroupTitle(metadata));
    summary.setIcon(resolveGroupIcon(metadata));
    summary.setDescription(metadata.getDescription());
    summary.setOrder(metadata.getOrder());
    summary.setTotalItems(statistics.getTotalItems());
    summary.setObsoleteItems(statistics.getObsoleteItems());
    summary.setReadonlyItems(statistics.getReadonlyItems());
    List<String> childGroupPaths = new ArrayList<>(snapshot.childGroupPathsByParent.getOrDefault(path, Collections.emptyList()));
    summary.setChildGroups(childGroupPaths.stream()
        .map(childPath -> toGroupSummary(snapshot, childPath))
        .sorted(groupSummaryComparator())
        .collect(Collectors.toList()));
    List<ConfigurationModel> items = new ArrayList<>(snapshot.directItemsByGroup.getOrDefault(path, Collections.emptyList()));
    summary.setItems(items.stream()
        .sorted(itemComparator())
        .map(this::toItemSummary)
        .collect(Collectors.toList()));
    return summary;
  }

  private ConfigurationWorkbenchItemSummary toItemSummary(ConfigurationModel model) {
    ConfigurationMetadata metadata = model.getMetadata();
    ConfigurationWorkbenchItemSummary summary = new ConfigurationWorkbenchItemSummary();
    summary.setKey(model.getKey());
    summary.setLabel(getItemLabel(model));
    summary.setDescription(metadata == null ? null : metadata.getDescription());
    summary.setGroupPath(normalizeGroupPath(metadata == null ? null : metadata.getGroupPath()));
    summary.setOrder(metadata == null ? null : metadata.getOrder());
    summary.setValue(model.getValue());
    summary.setRevision(model.getRevision());
    summary.setModifiable(metadata == null || metadata.isModifiable());
    summary.setObsolete(metadata != null && metadata.isObsolete());
    summary.setEditorSchema(schemaBuilder.build(metadata));
    summary.setMetadata(metadata);
    summary.setConfigValue(model.getConfigValue());
    return summary;
  }

  private ConfigurationWorkbenchStatistics buildStatistics(WorkbenchSnapshot snapshot, String path) {
    List<ConfigurationModel> scopedItems = snapshot.itemMap.values().stream()
        .filter(model -> isItemInScope(resolveEffectiveGroupPath(model.getMetadata()), path))
        .collect(Collectors.toList());

    ConfigurationWorkbenchStatistics statistics = new ConfigurationWorkbenchStatistics();
    statistics.setTotalItems(scopedItems.size());
    statistics.setModifiableItems((int) scopedItems.stream()
        .filter(model -> model.getMetadata() == null || model.getMetadata().isModifiable())
        .count());
    statistics.setReadonlyItems((int) scopedItems.stream()
        .filter(model -> model.getMetadata() != null && !model.getMetadata().isModifiable())
        .count());
    statistics.setObsoleteItems((int) scopedItems.stream()
        .filter(model -> model.getMetadata() != null && model.getMetadata().isObsolete())
        .count());
    statistics.setChildGroups(snapshot.childGroupPathsByParent.getOrDefault(path, Collections.emptyList()).size());
    return statistics;
  }

  private List<ConfigurationWorkbenchBreadcrumb> buildGroupBreadcrumbs(WorkbenchSnapshot snapshot, String path) {
    List<ConfigurationWorkbenchBreadcrumb> breadcrumbs = new ArrayList<>();
    breadcrumbs.add(buildGroupBreadcrumb(ROOT_PATH, ROOT_TITLE));

    if (ROOT_PATH.equals(path)) {
      return breadcrumbs;
    }

    if (UNGROUPED_PATH.equals(path)) {
      breadcrumbs.add(buildGroupBreadcrumb(path, UNGROUPED_TITLE));
      return breadcrumbs;
    }

    List<String> lineage = new ArrayList<>();
    String current = path;
    while (StringUtils.isNotBlank(current)) {
      lineage.add(current);
      current = getParentPath(current);
      if (ROOT_PATH.equals(current)) {
        break;
      }
    }
    Collections.reverse(lineage);
    lineage.forEach(groupPath -> breadcrumbs.add(buildGroupBreadcrumb(groupPath, getGroupTitle(snapshot.groupMetadataMap.get(groupPath)))));
    return breadcrumbs;
  }

  private List<ConfigurationWorkbenchBreadcrumb> buildItemBreadcrumbs(WorkbenchSnapshot snapshot, ConfigurationModel model) {
    List<ConfigurationWorkbenchBreadcrumb> breadcrumbs = new ArrayList<>(buildGroupBreadcrumbs(snapshot, resolveEffectiveGroupPath(model.getMetadata())));
    ConfigurationWorkbenchBreadcrumb itemBreadcrumb = new ConfigurationWorkbenchBreadcrumb();
    itemBreadcrumb.setType(ITEM_TYPE);
    itemBreadcrumb.setKey(model.getKey());
    itemBreadcrumb.setLabel(getItemLabel(model));
    breadcrumbs.add(itemBreadcrumb);
    return breadcrumbs;
  }

  private ConfigurationWorkbenchBreadcrumb buildGroupBreadcrumb(String path, String label) {
    ConfigurationWorkbenchBreadcrumb breadcrumb = new ConfigurationWorkbenchBreadcrumb();
    breadcrumb.setType(ROOT_TYPE);
    breadcrumb.setPath(path);
    breadcrumb.setLabel(label);
    return breadcrumb;
  }

  private ConfigurationGroupMetadata buildEffectiveGroupMetadata(String path, ConfigurationGroupMetadata metadata) {
    if (metadata != null) {
      return metadata;
    }
    if (UNGROUPED_PATH.equals(path)) {
      return new ConfigurationGroupMetadata()
          .setPath(path)
          .setTitle(UNGROUPED_TITLE)
          .setIcon("folder-open");
    }
    return new ConfigurationGroupMetadata()
        .setPath(path)
        .setTitle(deriveGroupTitle(path));
  }

  private ConfigurationGroupMetadata buildRootGroupMetadata() {
    return new ConfigurationGroupMetadata()
        .setPath(ROOT_PATH)
        .setTitle(ROOT_TITLE);
  }

  private boolean isVirtualGroup(String path, ConfigurationGroupMetadata metadata) {
    if (ROOT_PATH.equals(path) || UNGROUPED_PATH.equals(path)) {
      return true;
    }
    return metadata == null || StringUtils.isBlank(metadata.getTitle());
  }

  private boolean isItemInScope(String itemGroupPath, String scopePath) {
    if (ROOT_PATH.equals(scopePath)) {
      return true;
    }
    if (UNGROUPED_PATH.equals(scopePath)) {
      return UNGROUPED_PATH.equals(itemGroupPath);
    }
    return Objects.equals(itemGroupPath, scopePath)
        || StringUtils.startsWith(itemGroupPath, scopePath + ".");
  }

  private void addPathWithAncestors(TreeSet<String> paths, String path) {
    if (StringUtils.isBlank(path) || UNGROUPED_PATH.equals(path)) {
      return;
    }
    String current = path;
    while (StringUtils.isNotBlank(current)) {
      paths.add(current);
      current = getParentPath(current);
      if (ROOT_PATH.equals(current)) {
        break;
      }
    }
  }

  private String resolveEffectiveGroupPath(ConfigurationMetadata metadata) {
    String path = normalizeGroupPath(metadata == null ? null : metadata.getGroupPath());
    return path == null ? UNGROUPED_PATH : path;
  }

  private String normalizeRequestedPath(String path) {
    return StringUtils.defaultString(normalizeGroupPath(path), ROOT_PATH);
  }

  private String normalizeGroupPath(String path) {
    String normalized = StringUtils.trimToNull(path);
    if (normalized == null) {
      return null;
    }
    return StringUtils.strip(normalized, ".");
  }

  private String getParentPath(String path) {
    if (UNGROUPED_PATH.equals(path)) {
      return ROOT_PATH;
    }
    int index = path.lastIndexOf('.');
    if (index < 0) {
      return ROOT_PATH;
    }
    return path.substring(0, index);
  }

  private String deriveGroupTitle(String path) {
    String segment = path;
    int index = path.lastIndexOf('.');
    if (index >= 0) {
      segment = path.substring(index + 1);
    }
    return segment.replace('-', ' ').replace('_', ' ');
  }

  private String getGroupTitle(ConfigurationGroupMetadata metadata) {
    if (metadata == null) {
      return UNGROUPED_TITLE;
    }
    return StringUtils.defaultIfBlank(metadata.getTitle(), deriveGroupTitle(metadata.getPath()));
  }

  private String resolveGroupIcon(ConfigurationGroupMetadata metadata) {
    String icon = Optional.ofNullable(metadata)
        .map(ConfigurationGroupMetadata::getIcon)
        .filter(StringUtils::isNotBlank)
        .orElse(DEFAULT_GROUP_ICON);
    if (StringUtils.startsWith(icon, "fa ") || StringUtils.startsWith(icon, "icon-") || StringUtils.contains(icon, " ")) {
      return icon;
    }
    if (StringUtils.startsWith(icon, "fa-")) {
      return "fa " + icon;
    }
    if (StringUtils.equals(icon, "settings")) {
      return "fa fa-cog";
    }
    return "fa fa-" + icon;
  }

  private String getItemLabel(ConfigurationModel model) {
    return Optional.ofNullable(model.getMetadata())
        .map(ConfigurationMetadata::getLabel)
        .filter(StringUtils::isNotBlank)
        .orElse(model.getKey());
  }

  private Comparator<ConfigurationModel> itemComparator() {
    return Comparator
        .comparing((ConfigurationModel model) -> Optional.ofNullable(model.getMetadata()).map(ConfigurationMetadata::getOrder).orElse(Integer.MAX_VALUE))
        .thenComparing(this::getItemLabel, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(ConfigurationModel::getKey, String.CASE_INSENSITIVE_ORDER);
  }

  private Comparator<ConfigurationWorkbenchTreeNode> groupNodeComparator() {
    return Comparator
        .comparing((ConfigurationWorkbenchTreeNode node) -> Optional.ofNullable(node.getOrder()).orElse(Integer.MAX_VALUE))
        .thenComparing(ConfigurationWorkbenchTreeNode::getLabel, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(node -> node.getPath() == null ? ROOT_PATH : node.getPath(), String.CASE_INSENSITIVE_ORDER);
  }

  private Comparator<ConfigurationWorkbenchGroupSummary> groupSummaryComparator() {
    return Comparator
        .comparing((ConfigurationWorkbenchGroupSummary summary) -> Optional.ofNullable(summary.getOrder()).orElse(Integer.MAX_VALUE))
        .thenComparing(ConfigurationWorkbenchGroupSummary::getTitle, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(ConfigurationWorkbenchGroupSummary::getPath, String.CASE_INSENSITIVE_ORDER);
  }

  private static class WorkbenchSnapshot {
    private final Map<String, ConfigurationModel> itemMap;
    private final Map<String, ConfigurationGroupMetadata> groupMetadataMap;
    private final Map<String, List<String>> childGroupPathsByParent;
    private final Map<String, List<ConfigurationModel>> directItemsByGroup;

    private WorkbenchSnapshot(Map<String, ConfigurationModel> itemMap,
                              Map<String, ConfigurationGroupMetadata> groupMetadataMap,
                              Map<String, List<String>> childGroupPathsByParent,
                              Map<String, List<ConfigurationModel>> directItemsByGroup) {
      this.itemMap = itemMap;
      this.groupMetadataMap = groupMetadataMap;
      this.childGroupPathsByParent = childGroupPathsByParent;
      this.directItemsByGroup = directItemsByGroup;
    }
  }
}
