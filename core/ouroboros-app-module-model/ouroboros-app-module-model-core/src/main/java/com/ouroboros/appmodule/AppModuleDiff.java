package com.ouroboros.appmodule;

import java.beans.Transient;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

@SuppressWarnings({"unused"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface AppModuleDiff {
  static AppModuleDiff diff(AppModule original, AppModule modified) {
    if (original == null && modified == null) {
      return null;
    }
    return new Diff(original, modified);
  }

  DiffType getDiffType();

  String getFullName();

  String getParent();

  String getTitle();

  String getDescription();

  String getIcon();

  Integer getOrder();

  String getMenu();

  List<String> getSubMenus();

  List<String> getAuthorities();

  default AppModule patch(AppModule appModule) {
    return new PatchedAppModule(appModule, this);
  }

  @Transient
  default AppModule getAppModule() {
    return new AddedAppModule(this);
  }

  enum DiffType {
    ADD("add"),
    REMOVE("remove"),
    MODIFY("modify");
    private final String typeName;

    DiffType(String typeName) {
      this.typeName = typeName;
    }

    @JsonCreator
    public static DiffType fromTypeName(String typeName) {
      for (DiffType diffType : DiffType.values()) {
        if (diffType.getTypeName().equalsIgnoreCase(typeName)) {
          return diffType;
        }
      }
      return null;
    }

    @JsonValue
    public String getTypeName() {
      return typeName;
    }
  }

  class PatchedAppModule implements AppModule {
    private final AppModuleDiff diff;
    private final AppModule appModule;
    private final List<String> subMenus;
    private final List<String> authorities;

    public PatchedAppModule(AppModule appModule, AppModuleDiff diff) {
      this.diff = diff;
      this.appModule = appModule;
      this.subMenus = Optional.ofNullable(buildSubMenus())
          .orElseGet(Collections::emptyList);
      this.authorities = Optional.ofNullable(buildAuthorities())
          .orElseGet(Collections::emptyList);
    }


    private List<String> buildSubMenus() {
      if (ObjectUtils.isEmpty(diff.getSubMenus())) {
        return appModule.getSubMenus();
      }
      return diff.getSubMenus();
    }

    private List<String> buildAuthorities() {
      if (ObjectUtils.isEmpty(diff.getAuthorities())) {
        return appModule.getAuthorities();
      }
      return diff.getAuthorities();
    }

    @Override
    public String getFullName() {
      return diff.getFullName();
    }

    @Override
    public String getParent() {
      return Optional.ofNullable(diff.getParent()).orElse(appModule.getParent());
    }

    @Override
    public String getName() {
      return appModule.getName();
    }

    @Override
    public String getTitle() {
      return Optional.ofNullable(diff.getTitle()).orElse(appModule.getTitle());
    }

    @Override
    public String getDescription() {
      return Optional.ofNullable(diff.getDescription()).orElse(appModule.getDescription());
    }

    @Override
    public String getIcon() {
      return Optional.ofNullable(diff.getIcon()).orElse(appModule.getIcon());
    }

    @Override
    public Integer getOrder() {
      return Optional.ofNullable(diff.getOrder()).orElse(appModule.getOrder());
    }

    @Override
    public String getMenu() {
      return Optional.ofNullable(diff.getMenu())
          .filter(StringUtils::isNotBlank)
          .orElseGet(appModule::getMenu);
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

  class AddedAppModule implements AppModule {
    private final AppModuleDiff diff;

    public AddedAppModule(AppModuleDiff diff) {
      this.diff = diff;
    }

    @Override
    public String getFullName() {
      return diff.getFullName();
    }

    @Override
    public String getParent() {
      return diff.getParent();
    }

    @Override
    public String getName() {
      var parts = diff.getFullName().split("\\.");
      return parts[parts.length - 1];
    }

    @Override
    public String getTitle() {
      return diff.getTitle();
    }

    @Override
    public String getDescription() {
      return diff.getDescription();
    }

    @Override
    public String getIcon() {
      return diff.getIcon();
    }

    @Override
    public Integer getOrder() {
      return diff.getOrder();
    }

    @Override
    public String getMenu() {
      return getFullName();
    }

    @Override
    public List<String> getSubMenus() {
      return diff.getSubMenus();
    }

    @Override
    public List<String> getAuthorities() {
      return diff.getAuthorities();
    }
  }

  class Diff implements AppModuleDiff {
    final AppModule original;
    final AppModule modified;
    final String fullName;
    final DiffType diffType;

    public Diff(AppModule original, AppModule modified) {
      this.original = original;
      this.modified = modified;
      this.fullName = Optional.ofNullable(modified).orElse(original).getFullName();
      this.diffType = modified == null ? DiffType.REMOVE : original == null ? DiffType.ADD : DiffType.MODIFY;
    }

    public Diff(String fullName) {
      this.original = null;
      this.modified = null;
      this.fullName = fullName;
      this.diffType = DiffType.REMOVE;
    }

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
      var originalParent = Optional.ofNullable(original).map(AppModule::getParent).orElse(null);
      var modifiedParent = Optional.ofNullable(modified).map(AppModule::getParent).orElse(null);
      if (Objects.equals(originalParent, modifiedParent)) {
        return null;
      }
      return modifiedParent;
    }

    @Override
    public String getTitle() {
      var originalTitle = Optional.ofNullable(original).map(AppModule::getTitle).orElse(null);
      var modifiedTitle = Optional.ofNullable(modified).map(AppModule::getTitle).orElse(null);
      if (Objects.equals(originalTitle, modifiedTitle)) {
        return null;
      }
      return modifiedTitle;
    }

    @Override
    public String getDescription() {
      var originalDescription = Optional.ofNullable(original).map(AppModule::getDescription).orElse(null);
      var modifiedDescription = Optional.ofNullable(modified).map(AppModule::getDescription).orElse(null);
      if (Objects.equals(originalDescription, modifiedDescription)) {
        return null;
      }
      return modifiedDescription;
    }

    @Override
    public String getIcon() {
      var originalIcon = Optional.ofNullable(original).map(AppModule::getIcon).orElse(null);
      var modifiedIcon = Optional.ofNullable(modified).map(AppModule::getIcon).orElse(null);
      if (Objects.equals(originalIcon, modifiedIcon)) {
        return null;
      }
      return modifiedIcon;
    }

    @Override
    public Integer getOrder() {
      var originalOrder = Optional.ofNullable(original).map(AppModule::getOrder).orElse(null);
      var modifiedOrder = Optional.ofNullable(modified).map(AppModule::getOrder).orElse(null);
      if (Objects.equals(originalOrder, modifiedOrder)) {
        return null;
      }
      return modifiedOrder;
    }

    @Override
    public String getMenu() {
      var originalMenu = Optional.ofNullable(original).map(AppModule::getMenu).orElse(null);
      var modifiedMenu = Optional.ofNullable(modified).map(AppModule::getMenu).orElse(null);
      if (Objects.equals(originalMenu, modifiedMenu)) {
        return null;
      }
      return modifiedMenu;
    }

    /**
     * 此属性只对增加类型的Diff有效
     *
     * @return 子菜单列表
     */
    @Override
    public List<String> getSubMenus() {
      if (getDiffType() == DiffType.ADD) {
        return Optional.ofNullable(modified).map(AppModule::getSubMenus).orElseGet(Collections::emptyList);
      }
      return null;
    }

    /**
     * 此属性只对增加类型的Diff有效
     *
     * @return 子菜单列表
     */
    @Override
    public List<String> getAuthorities() {
      if (getDiffType() == DiffType.ADD) {
        return Optional.ofNullable(modified).map(AppModule::getAuthorities).orElseGet(Collections::emptyList);
      }
      return null;
    }
  }
}
