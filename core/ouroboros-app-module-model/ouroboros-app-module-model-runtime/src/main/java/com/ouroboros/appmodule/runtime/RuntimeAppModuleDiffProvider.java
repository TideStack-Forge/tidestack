package com.ouroboros.appmodule.runtime;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.AppContext;
import com.ouroboros.appmodule.AppModuleDiff;
import com.ouroboros.appmodule.AppModuleDiffProvider;
import com.ouroboros.metadata.resource.ClasspathMetadataFiles;
import com.ouroboros.util.Json;

@Component
public class RuntimeAppModuleDiffProvider {
  private static final String DIFF_FILE_NAME = "modules.diff.json";
  @Autowired
  AppContext appContext;

  public RuntimeAppModuleDiffProvider() {
    SingletonHolder.INSTANCE = this;
  }

  public List<AppModuleDiff> getAppModuleDiffs() {
    List<AppModuleDiff> diffs = new ArrayList<>();
    new ClasspathMetadataFiles().legacyThenMetadataResourcesForOverride(DIFF_FILE_NAME)
        .stream()
        .map(resource -> resource.content())
        .forEach(content -> diffs.addAll(parseDiffs(content)));

    var homePath = appContext.getHomePath();
    var diffFile = Paths.get(homePath, DIFF_FILE_NAME).toFile();
    var diffJson = Try.of(() -> FileUtils.readFileToString(diffFile, "UTF-8"))
        .getOrElseGet(e -> "");
    if (StringUtils.isBlank(diffJson)) {
      return diffs;
    }
    diffs.addAll(parseDiffs(diffJson));
    return diffs;
  }

  private List<AppModuleDiff> parseDiffs(String diffJson) {
    if (StringUtils.isBlank(diffJson)) {
      return Collections.emptyList();
    }
    return new ArrayList<AppModuleDiff>(Json.toList(new TypeReference<List<Diff>>() {
    }, diffJson));
  }

  @Priority(0)
  public static class SingletonHolder implements AppModuleDiffProvider {
    static RuntimeAppModuleDiffProvider INSTANCE;

    @Override
    public List<AppModuleDiff> getAppModuleDiffs() {
      return Optional.ofNullable(INSTANCE)
          .map(RuntimeAppModuleDiffProvider::getAppModuleDiffs)
          .orElseGet(Collections::emptyList);
    }
  }

  static class Diff implements AppModuleDiff {
    private DiffType diffType;
    private String fullName;
    private String parent;
    private String title;
    private String description;
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
