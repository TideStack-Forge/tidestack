package com.ouroboros.appmodule.dev;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleDiff;
import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.annotation.DevModel;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.VCSManager;
import com.ouroboros.vcs.VCSManagerCenter;

@Component
@SuppressWarnings({"unused"})
public class AppModuleAggregator {
  private final static Logger logger = LoggerFactory.getLogger(AppModuleAggregator.class);
  private static final String DIFF_FILE_NAME = "modules.diff.json";

  @Autowired(required = false)
  private List<DevAppModuleProvider> devAppModuleProviders;

  @Autowired
  private VCSManagerCenter vcsManagerCenter;

  @Autowired
  private DevelopWorkspaceContext developWorkspaceContext;

  public List<AppModule> getAppModules() {
    String appName = DevAppHolder.getAppName();
    Comparator<AppModule> sortByOrder = Comparator
        .comparingInt(m -> m.getOrder() == null ? 0 : m.getOrder());

    List<AppModule> devAppModules = Optional.ofNullable(devAppModuleProviders)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(providers -> providers.getAppModules(appName))
        .flatMap(List::stream)
        .collect(Collectors.toList());

    return devAppModules
        .stream()
        // 去重
        .collect(() -> new TreeMap<String, AppModule>(String::compareToIgnoreCase), (map, item) -> map.put(item.getFullName(), item), TreeMap::putAll)
        .values()
        .stream()
        .sorted(sortByOrder)
        .collect(Collectors.toList());
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public List<AppModuleDiff> getDiffs() {
    var vcsManager = getVcsManager();
    var diffFile = vcsManager.getFile(DIFF_FILE_NAME);
    if (!diffFile.exists()) {
      return Collections.emptyList();
    }
    var diffJson = diffFile.getContent();
    return (List) Json.toList(new TypeReference<List<Diff>>() {
    }, diffJson.trim().isEmpty() ? "[]" : diffJson);
  }

  public void saveDiffs(List<AppModuleDiff> diffs) {
    var vcsManager = getVcsManager();
    var diffJson = Json.toPrettyJsonString(diffs);
    var diffFile = vcsManager.getFile(DIFF_FILE_NAME);
    if (diffFile.exists()) {
      var updateResult = vcsManager.updateFile(DIFF_FILE_NAME)
          .content(diffJson)
          .committer(getUserIdentity())
          .commit("更新模块差异文件");
      if (!updateResult.isFailure()) {
        logger.error("更新模块差异文件失败");
      }
      return;
    }
    var createResult = vcsManager.createFile(DIFF_FILE_NAME)
        .content(diffJson)
        .committer(getUserIdentity())
        .commit("创建模块差异文件");
    if (!createResult.isFailure()) {
      logger.error("创建模块差异文件失败");
    }
  }

  VCSManager getVcsManager() {
    return vcsManagerCenter.getVcsManager(DevAppHolder.getAppName());
  }

  String getUserIdentity() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(SecurityIdentity::getUserIdentity)
        .orElse(null);
  }

  static class Diff implements AppModuleDiff {
    private DiffType diffType;
    private String fullName;
    private String parent;
    private String title;
    private String description;
    private String url;
    private String icon;
    private Integer order;
    private String menu;
    private List<String> subMenus;
    private List<String> authorities;

    @Override
    public DiffType getDiffType() {
      return diffType;
    }

    @Override
    public String getFullName() {
      return fullName;
    }

    @Override
    public String getParent() {
      return parent;
    }

    @Override
    public String getTitle() {
      return title;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public String getIcon() {
      return icon;
    }

    @Override
    public Integer getOrder() {
      return order;
    }

    @Override
    public String getMenu() {
      return menu;
    }

    @Override
    public List<String> getSubMenus() {
      return subMenus;
    }

    @Override
    public List<String> getAuthorities() {
      return authorities;
    }
  }
}
