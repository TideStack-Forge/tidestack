package com.ouroboros.appmodule;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.model.annotation.ProCodeModel;
import com.ouroboros.appmodule.model.AuthoritiesAppModule;
import com.ouroboros.appmodule.model.BlankAppModule;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.util.SPI;

@SuppressWarnings("unused")
public class ProCodeAuthorityAppModuleProjector implements AppModuleProvider {
  @Override
  public List<AppModule> getAppModules() {
    return buildAppModules();
  }

  List<AppModule> buildAppModules() {
    var allProCodeAuthorities = getAllProCodeAuthorities();
    var appModuleMap = new HashMap<String, AppModule>();

    // 先根据权限创建模块
    allProCodeAuthorities.stream()
        .collect(Collectors.groupingBy(auth -> Optional.ofNullable(auth.getParent()).orElse(auth.getFullName())))
        .forEach((parent, authorities) -> {
          var appModule = new AuthoritiesAppModule(authorities);
          appModuleMap.put(parent, appModule);
        });

    // 补充父级模块
    for (var authority : allProCodeAuthorities) {
      var parent = authority.getParent();
      if (ObjectUtils.isEmpty(parent) || !parent.contains(".")) {
        continue;
      }

      // 先跳过自己
      parent = parent.substring(0, parent.lastIndexOf("."));
      while (ObjectUtils.isNotEmpty(parent)) {
        if (!appModuleMap.containsKey(parent)) {
          appModuleMap.put(parent, new BlankAppModule(parent));
        }
        parent = parent.contains(".")
            ? parent.substring(0, parent.lastIndexOf("."))
            : null;
      }
    }

    // 排序输出
    return appModuleMap.values()
        .stream()
        .sorted(Comparator.comparingInt(m -> Optional.ofNullable(m.getOrder()).orElse(0)))
        .collect(Collectors.toList());
  }

  List<Authority> getAllProCodeAuthorities() {
    return SPI.getCachedSortedServiceStream(AuthorityProvider.class)
        .map(AuthorityProvider::getAllAuthorities)
        // 检查 Authority 的类是否有 @ProCodeModel 注解
        .flatMap(list -> list.stream().filter(authority -> authority.getClass().isAnnotationPresent(ProCodeModel.class)))
        .collect(Collectors.toList());

  }
}
