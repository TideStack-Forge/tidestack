package com.ouroboros.appmodule;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.ouroboros.appmodule.model.*;
import com.ouroboros.menu.MenuModel;
import com.ouroboros.security.authority.Authority;

public interface AppModuleIntegrator {
  List<AppModule> getAppModules();

  default List<MenuModel> rearrangeMenus(List<MenuModel> menus) {
    var appModuleList = getAppModules();
    Function<AppModule, Stream<MenuModel>> getAllMenus = module -> {
      MenuModel mainMenu = StringUtils.isEmpty(module.getMenu())
          ? null
          : menus.stream()
          .filter(m -> m.getFullName().equalsIgnoreCase(module.getMenu()))
          .map(m -> (MenuModel) new ArrangedModuleMenu(m, module))
          .findFirst()
          .orElseGet(() -> new PlacementModuleMenu(module));
      Stream<MenuModel> subMenus = Optional.ofNullable(module.getSubMenus())
          .orElseGet(Collections::emptyList)
          .stream()
          .map(subMenu -> menus.stream()
              .filter(m -> m.getFullName().equalsIgnoreCase(subMenu))
              .map(m -> (MenuModel) new ArrangedModuleSubMenu(m, module))
              .findFirst())
          .filter(Optional::isPresent)
          .map(Optional::get);
      return Stream.concat(mainMenu == null ? Stream.empty() : Stream.of(mainMenu), subMenus);
    };
    return appModuleList.stream()
        .flatMap(getAllMenus)
        .filter(ObjectUtils::isNotEmpty)
        .sorted(Comparator.comparingInt(m -> Optional.ofNullable(m.getOrder()).orElse(0)))
        .collect(Collectors.toList());
  }

  default List<Authority> rearrangeAuthorities(Map<String, Authority> authorityMap) {
    var appModuleList = getAppModules();
    var modulesByFullName = new LinkedHashMap<String, AppModule>();
    appModuleList.forEach(module -> modulesByFullName.putIfAbsent(module.getFullName(), module));

    var selectedAuthorities = new LinkedHashMap<String, AuthorityCandidate>();
    Function<AppModule, Stream<AuthorityCandidate>> getAuthorities = module -> {
      if (ObjectUtils.isEmpty(module.getAuthorities())) {
        return Stream.of(new AuthorityCandidate(new PlacementModuleAuthority(module), module, true));
      }
      return module.getAuthorities()
          .stream()
          .map(authorityMap::get)
          .filter(Objects::nonNull)
          .map(a -> new AuthorityCandidate(new ArrangedModuleAuthority(a, module), module, false));
    };

    appModuleList.stream()
        .flatMap(getAuthorities)
        .forEach(candidate -> selectAuthority(selectedAuthorities, candidate));

    var pendingParents = new ArrayDeque<String>();
    selectedAuthorities.values().stream()
        .map(candidate -> candidate.authority().getParent())
        .filter(Objects::nonNull)
        .forEach(pendingParents::add);

    var visitedParents = new HashSet<String>();
    while (!pendingParents.isEmpty()) {
      var parentFullName = pendingParents.remove();
      if (!visitedParents.add(parentFullName)) {
        continue;
      }

      var selectedParent = selectedAuthorities.get(parentFullName);
      if (selectedParent != null) {
        addParentIfPresent(pendingParents, selectedParent.authority().getParent());
        continue;
      }

      var parentModule = modulesByFullName.get(parentFullName);
      if (parentModule == null) {
        continue;
      }

      selectAuthority(selectedAuthorities,
          new AuthorityCandidate(new PlacementModuleAuthority(parentModule), parentModule, true));
      addParentIfPresent(pendingParents, parentModule.getParent());
    }

    return selectedAuthorities.values().stream()
        .map(AuthorityCandidate::authority)
        .sorted(Comparator.comparingInt(m -> Optional.ofNullable(m.getOrder()).orElse(0)))
        .collect(Collectors.toList());
  }

  private static void addParentIfPresent(Deque<String> pendingParents, String parentFullName) {
    if (parentFullName != null) {
      pendingParents.add(parentFullName);
    }
  }

  private static void selectAuthority(Map<String, AuthorityCandidate> selectedAuthorities,
      AuthorityCandidate candidate) {
    var fullName = candidate.authority().getFullName();
    var current = selectedAuthorities.get(fullName);
    if (current == null || isPreferred(candidate, current)) {
      selectedAuthorities.put(fullName, candidate);
    }
  }

  private static boolean isPreferred(AuthorityCandidate candidate, AuthorityCandidate current) {
    if (candidate.placement() != current.placement()) {
      return !candidate.placement();
    }

    if (!candidate.placement()) {
      var candidateIsOwnModule = Objects.equals(candidate.authority().getFullName(), candidate.module().getFullName());
      var currentIsOwnModule = Objects.equals(current.authority().getFullName(), current.module().getFullName());
      if (candidateIsOwnModule != currentIsOwnModule) {
        return candidateIsOwnModule;
      }
    }

    return Optional.ofNullable(candidate.authority().getOrder()).orElse(0)
        < Optional.ofNullable(current.authority().getOrder()).orElse(0);
  }

  record AuthorityCandidate(Authority authority, AppModule module, boolean placement) {
  }
}
