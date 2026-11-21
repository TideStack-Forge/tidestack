package com.ouroboros.businessmodel;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.AppContext;
import com.ouroboros.appmodule.AppModule;
import com.ouroboros.appmodule.AppModuleProvider;
import com.ouroboros.businessmodel.model.ModuleAppModule;
import com.ouroboros.businessmodel.model.ModuleAuthority;
import com.ouroboros.businessmodel.model.ModuleMenu;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.menu.MenuModelProvider;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;

@Component
public class ModuleExportProvider implements MenuModelProvider, AuthorityProvider {
  private final static Logger logger = LoggerFactory.getLogger(ModuleExportProvider.class);
  private final static String BUSINESS_MODEL_PATH = "business-model";
  private final static String MODULE_FILE_NAME = "modules.json";
  private final AppContext context;
  private Map<String, MenuModel> menuModelMap;
  private List<Authority> authorities;
  private List<AppModule> appModules;

  @Autowired
  BusinessModelExport businessModelExport;

  public ModuleExportProvider(AppContext context) {
    this.context = context;
    SingletonHolder.instance = this;
  }

  @EventListener
  @Order(19)
  @SuppressWarnings("unused")
  public void onMetadataRefreshed(OuroborosContextRefreshedEvent event) {
    var homePath = context.getHomePath();
    menuModelMap = getMenuModelMap(homePath);
    authorities = getAuthorityList(homePath);
    appModules = getAppModules(homePath);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private List<AppModule> getAppModules(String homePath) {
    var moduleFilePath = Paths.get(homePath, BUSINESS_MODEL_PATH, MODULE_FILE_NAME);
    var fileContent = Try.of(() -> String.join("", Files.readAllLines(moduleFilePath))).getOrElse("");
    if (fileContent.isEmpty()) {
      return Collections.emptyList();
    }
    return (List) Json.tryToList(new TypeReference<List<ModuleAppModule>>() {
        }, fileContent)
        .onFailure(throwable -> logger.error("无法从模块配置文件中加载模块", throwable))
        .getOrElse(Collections::emptyList);
  }

  private Map<String, MenuModel> getMenuModelMap(String homePath) {
    var moduleFilePath = Paths.get(homePath, BUSINESS_MODEL_PATH, MODULE_FILE_NAME);
    var fileContent = Try.of(() -> String.join("", Files.readAllLines(moduleFilePath))).getOrElse("");
    if (fileContent.isEmpty()) {
      return Collections.emptyMap();
    }
    return Json.tryToList(new TypeReference<List<ModuleMenu>>() {
        }, fileContent)
        .onFailure(throwable -> logger.error("无法从模块配置文件中加载菜单", throwable))
        .getOrElse(Collections::emptyList)
        .stream()
        .collect(HashMap::new,
            (map, item) -> map.put(item.getFullName(), item),
            HashMap::putAll);
  }

  @SuppressWarnings({"unchecked"})
  private List<Authority> getAuthorityList(String homePath) {
    var moduleFilePath = Paths.get(homePath, BUSINESS_MODEL_PATH, MODULE_FILE_NAME);
    var fileContent = Try.of(() -> String.join("", Files.readAllLines(moduleFilePath))).getOrElse("");
    if (fileContent.isEmpty()) {
      return Collections.emptyList();
    }
    // var authorityNameList = businessModelExport.getAuthorityNameList();
    return Json.tryToList(new TypeReference<List<Map<String, ?>>>() {
        }, fileContent)
        .onFailure(throwable -> logger.error("无法从模块配置文件中加载权限项", throwable))
        .getOrElse(Collections::emptyList)
        .stream()
        .map(map -> Maps.pick(map, "fullName", "title", "order"))
        .map(map -> Maps.tryToBean(ModuleAuthority.class, (Map<String, Object>) map))
        // TODO: 审查 tryToBean 错误处理
        .filter(Try::isSuccess)
        .map(Try::get)
        // TODO: 暂时先不过滤，因为空模块有可能会在模块编排的时候变成非空的
        // .filter(item -> authorityNameList.stream().anyMatch(authority -> authority.startsWith(item.getFullName() + ".")))
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, MenuModel> getAllMenus() {
    return menuModelMap;
  }

  @Override
  public List<Authority> getAuthority(String fullName) {
    return authorities
        .stream()
        .filter(authority -> authority.getFullName().equals(fullName))
        .collect(Collectors.toList());
  }

  @Override
  public List<Authority> getAllAuthorities() {
    return authorities;
  }


  @Priority(5)
  public static class SingletonHolder implements MenuModelProvider, AppModuleProvider {
    public static ModuleExportProvider instance;

    @Override
    public Map<String, MenuModel> getAllMenus() {
      return instance.getAllMenus();
    }

    @Override
    public List<AppModule> getAppModules() {
      return instance.appModules;
    }
  }
}
