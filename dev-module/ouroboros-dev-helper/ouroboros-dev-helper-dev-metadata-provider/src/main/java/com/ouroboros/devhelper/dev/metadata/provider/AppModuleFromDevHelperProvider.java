package com.ouroboros.devhelper.dev.metadata.provider;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.dev.DevAppModuleProvider;
import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.dev.metadata.MetadataProviderMixer;

@Component
@Order(-1)
// 来自于 dev-helper 的模块定义应当被低代码定义的模块覆盖，低代码 DevAppModuleProvider 都没有 Order 注解，注入成 List 的时候本实例会
// 在前面，然后是低代码的，这样就能通过 collect 成 Map 的方式覆盖本实例导出的模型
public class AppModuleFromDevHelperProvider implements DevAppModuleProvider {
  private final Set<String> SOURCE_CLASS_BLACK_LIST = Arrays
      .asList("com.ouroboros.businessmodel.ModuleExportProvider$SingletonHolder", "com.ouroboros.businessmodel.BusinessModelExport$SingletonHolder")
      .stream()
      .collect(Collectors.toSet());
  private final MetadataProviderMixer mixer;

  public AppModuleFromDevHelperProvider(List<MetadataProvider> providers) {
    this.mixer = MetadataProviderMixer.getDistinctMixer(providers);
  }

  @Override
  public List<AppModule> getAppModules(String appName) {
    var metadataList = mixer.getAppAllMetadataByModule(appName, "app-module");
    return metadataList.stream()
        .filter(metadata -> !SOURCE_CLASS_BLACK_LIST.contains(metadata.getSourceClassName()))
        .map(AppModuleWrapper::new)
        .collect(Collectors.toList());
  }

  public static class AppModuleWrapper implements AppModule {
    Map<String, Object> details;

    public AppModuleWrapper(Metadata metadata) {
      this.details = Optional.ofNullable(metadata.getDetails())
          .orElseGet(Collections::emptyMap);
    }

    @Override
    public String getFullName() {
      return Optional.ofNullable(details.get("fullName"))
          .map(Object::toString)
          .orElse("");
    }

    @Override
    public String getParent() {
      return Optional.ofNullable(details.get("parent"))
          .map(Object::toString)
          .orElse("");
    }

    @Override
    public String getName() {
      var name = Optional.ofNullable(details.get("name"))
          .map(Object::toString)
          .filter(StringUtils::isNotBlank);
      if (name.isPresent()) {
        return name.get();
      }

      // 从 fullName 推
      var fullName = getFullName();
      var lastDot = fullName.lastIndexOf(".");
      if (lastDot > 0) {
        return fullName.substring(lastDot + 1);
      }
      return fullName;
    }

    @Override
    public String getTitle() {
      return Optional.ofNullable(details.get("title"))
          .map(Object::toString)
          .orElse("");
    }

    @Override
    public String getDescription() {
      return Optional.ofNullable(details.get("description"))
          .map(Object::toString)
          .orElse("");
    }

    @Override
    public String getIcon() {
      return Optional.ofNullable(details.get("icon"))
          .map(Object::toString)
          .orElse("");
    }

    @Override
    public Integer getOrder() {
      return Optional.ofNullable(details.get("order"))
          .map(Object::toString)
          .map(Integer::parseInt)
          .orElse(0);
    }

    @Override
    public String getMenu() {
      return Optional.ofNullable(details.get("menu"))
          .map(Objects::toString)
          .orElse(null);
    }

    @Override
    public List<String> getSubMenus() {
      return Optional.ofNullable(details.get("subMenus"))
          .filter(List.class::isInstance)
          .map(List.class::cast)
          .filter(l -> ((List<Object>) l).stream().allMatch(String.class::isInstance))
          .map(l -> (List<String>) l)
          .orElseGet(Collections::emptyList);
    }

    @Override
    public List<String> getAuthorities() {
      return Optional.ofNullable(details.get("authorities"))
          .filter(List.class::isInstance)
          .map(List.class::cast)
          .filter(l -> ((List<Object>) l).stream().allMatch(String.class::isInstance))
          .map(l -> (List<String>) l)
          .orElseGet(Collections::emptyList);
    }
  }
}
