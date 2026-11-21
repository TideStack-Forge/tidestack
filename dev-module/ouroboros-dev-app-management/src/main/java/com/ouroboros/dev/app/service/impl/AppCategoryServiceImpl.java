package com.ouroboros.dev.app.service.impl;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.Tuple;
import io.vavr.control.Try;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.model.AppCategoryPO;
import com.ouroboros.dev.app.model.AppCategoryDTO;
import com.ouroboros.dev.app.service.AppCategoryService;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.util.Json;
import com.ouroboros.util.Trees;

@Service
public class AppCategoryServiceImpl implements AppCategoryService {
  final static String CATEGORY_FILE_NAME = "category.json";
  final static String CATEGORY_APP_LINK_FILE_NAME = "category-app-link.json";
  @Autowired
  AppManagementService appManagementService;
  @Autowired
  DevelopWorkspaceContext developWorkspaceContext;
  private List<AppCategoryDTO> categories = new ArrayList<>();
  private Map<String, List<String>> categoryAppLinks = new LinkedHashMap<>();

  @PostConstruct
  void init() {
    reloadCategory();
    reloadCategoryAppLink();
  }

  private void reloadCategory() {
    var categoryFilePath = Paths.get(getWorkspace(), CATEGORY_FILE_NAME);
    if (Files.exists(categoryFilePath)) {
      Try.run(() -> {
        var lines = Files.readAllLines(categoryFilePath);
        var content = String.join("\n", lines);
        var categoryList = Optional.ofNullable(Json.toList(new TypeReference<List<AppCategoryPO>>() {
        }, content)).orElseGet(Collections::emptyList);
        var categoryTree = Trees.<AppCategoryPO, AppCategoryDTO>mapTreeDown(categoryList,
            po -> po.getChildren().stream().sorted(Comparator.comparing(AppCategoryPO::getOrder)).collect(Collectors.toList()),
            AppCategoryDTO::setChildren, (poNode, voParent) -> {
              var voNode = new AppCategoryDTO();
              var parentPath = voParent == null ? "" : voParent.getFullPath();
              voNode.setName(poNode.getName());
              voNode.setParentPath(parentPath);
              voNode.setOrder(poNode.getOrder() == null ? 0 : poNode.getOrder());
              voNode.setFullPath((parentPath.isEmpty() ? "" : (parentPath + ".")) + voNode.getName());
              voNode.setIcon(poNode.getIcon());
              return voNode;
            });
        categories = categoryTree.stream().sorted(Comparator.comparing(AppCategoryDTO::getOrder)).collect(Collectors.toList());
      });
    }
  }

  private void reloadCategoryAppLink() {
    var categoryAppLinkFilePath = Paths.get(getWorkspace(), CATEGORY_APP_LINK_FILE_NAME);
    if (Files.exists(categoryAppLinkFilePath)) {
      Try.run(() -> {
        var lines = Files.readAllLines(categoryAppLinkFilePath);
        var content = String.join("\n", lines);
        var categoryAppLinkMap = Optional.ofNullable(Json.toMap(content)).orElseGet(Collections::emptyMap);
        categoryAppLinks = categoryAppLinkMap.entrySet()
            .stream()
            .map(entry -> {
              if (entry.getValue() instanceof List<?> value && value.stream().allMatch(String.class::isInstance)) {
                return Tuple.of(entry.getKey(), value.stream().map(String.class::cast).collect(Collectors.toList()));
              }
              return null;
            })
            .filter(Objects::nonNull)
            .collect(LinkedHashMap::new, (map, tuple) -> map.put(tuple._1, tuple._2), LinkedHashMap::putAll);
      });
    }
  }

  private String getWorkspace() {
    return developWorkspaceContext.getDevRootDir();
  }

  private Boolean hasCategory(String fullPath) {
    return categories.stream().flatMap(Trees.streamFlatter(vo -> vo.getChildren().stream()))
        .anyMatch(c -> c.getFullPath().equals(fullPath));
  }

  @Override
  public Try<Void> addCategory(AppCategoryDTO category) {
    return Try.run(() -> {
      if (hasCategory(category.getFullPath())) {
        throw new BusinessException("类型已存在");
      }
      if (category.getParentPath().isEmpty()) {
        if (category.getOrder() == null) {
          category.setOrder(categories.stream().map(c -> c.getOrder() + 1).max(Integer::compareTo).orElse(0));
        }
        var categoryTree = new ArrayList<>(categories);
        categoryTree.add(category);
        categories = categoryTree
            .stream()
            .sorted(Comparator.comparingInt(AppCategoryDTO::getOrder))
            .collect(Collectors.toList());
        saveCategories();
        return;
      }
      categories = Trees.<AppCategoryDTO, AppCategoryDTO>mapTree(categories, AppCategoryDTO::getChildren, (voNode, children) -> {
        if (category.getParentPath().equals(voNode.getFullPath())) {
          var newVo = new AppCategoryDTO();
          newVo.setName(voNode.getName());
          newVo.setParentPath(voNode.getParentPath());
          newVo.setFullPath(voNode.getFullPath());
          newVo.setOrder(voNode.getOrder());
          newVo.setIcon(voNode.getIcon());
          List<AppCategoryDTO> newChildren = new ArrayList<>(children);
          if (category.getOrder() == null) {
            category.setOrder(children.stream().max(Comparator.comparingInt(AppCategoryDTO::getOrder)).map(c -> c.getOrder() + 1).orElse(0));
          }
          newChildren.add(category);
          newVo.setChildren(newChildren.stream().sorted(Comparator.comparingInt(AppCategoryDTO::getOrder)).collect(Collectors.toList()));
          return newVo;
        }
        voNode.setChildren(children);
        return voNode;
      });
      saveCategories();
    });
  }

  @Override
  public Try<Void> deleteCategory(String fullPath) {
    return Try.run(() -> {
      if (!hasCategory(fullPath)) {
        return;
      }
      if (categoryAppLinks.containsKey(fullPath)) {
        categoryAppLinks.remove(fullPath);
        saveCategoryAppLinks();
      }
      var categoryTree = Trees.<AppCategoryDTO, AppCategoryDTO>mapTree(categories.stream().filter(c -> !c.getFullPath().equals(fullPath)).collect(Collectors.toList()),
          vo -> vo.getChildren().stream().filter(c -> !c.getFullPath().equals(fullPath)).collect(Collectors.toList()),
          (voNode, children) -> {
            voNode.setChildren(children);
            return voNode;
          });
      categories = new CopyOnWriteArrayList<>(categoryTree);
      saveCategories();
    });
  }

  @Override
  public Try<Void> updateCategory(String fullPath, AppCategoryDTO newCategory) {
    return Try.run(() -> {
      if (!hasCategory(fullPath)) {
        throw new BusinessException("原类型已不存在");
      }
      var newPath = newCategory.getFullPath();
      if (!newPath.equals(fullPath) && hasCategory(newPath)) {
        throw new BusinessException("新类型已存在");
      }
      var categoryTree = Trees.<AppCategoryDTO, AppCategoryDTO>mapTree(categories.stream().filter(c -> !c.getFullPath().equals(fullPath)).collect(Collectors.toList()),
          vo -> vo.getChildren().stream()
              .filter(c -> !c.getFullPath().equals(fullPath))
              .collect(Collectors.toList()),
          (voNode, children) -> {
            if (!newCategory.getParentPath().equals(voNode.getFullPath())) {
              voNode.setChildren(children);
              return voNode;
            }
            var newVo = new AppCategoryDTO();
            newVo.setFullPath(voNode.getFullPath());
            newVo.setName(voNode.getName());
            newVo.setParentPath(voNode.getParentPath());
            newVo.setOrder(voNode.getOrder());
            newVo.setIcon(voNode.getIcon());
            List<AppCategoryDTO> newChildren = new ArrayList<>(children);
            newChildren.add(newCategory);
            newVo.setChildren(newChildren.stream().sorted(Comparator.comparingInt(AppCategoryDTO::getOrder)).collect(Collectors.toList()));
            return newVo;
          });
      if (newCategory.getParentPath().isEmpty()) {
        categoryTree.add(newCategory);
        categoryTree = categoryTree.stream()
            .sorted(Comparator.comparingInt(AppCategoryDTO::getOrder))
            .collect(Collectors.toList());
      }
      categories = new CopyOnWriteArrayList<>(categoryTree);
      saveCategories();
      moveCategoryApps(fullPath, newPath);
    });
  }

  @Override
  public Try<List<AppCategoryDTO>> listCategories() {
    return Try.success(Collections.unmodifiableList(categories));
  }

  @Override
  public void addApp(String fullPath, String appName) {
    var list = categoryAppLinks.get(fullPath);
    if (list == null) {
      list = new CopyOnWriteArrayList<>();
      categoryAppLinks.put(fullPath, list);
    }
    if (!list.contains(appName)) {
      list.add(appName);
      saveCategoryAppLinks();
    }
  }

  @Override
  public void removeApp(String fullPath, String appName) {
    var list = categoryAppLinks.get(fullPath);
    if (list == null) {
      return;
    }
    list.remove(appName);
    saveCategoryAppLinks();
  }

  @Override
  public void moveApp(String fullPath, String appName, String newFullPath) {
    var list = categoryAppLinks.get(fullPath);
    if (list != null) {
      list.remove(appName);
    }
    if (!newFullPath.isEmpty()) {
      addApp(newFullPath, appName);
    } else {
      saveCategoryAppLinks();
    }
  }

  @Override
  public List<String> getApps(String fullPath) {
    var list = categoryAppLinks.get(fullPath);
    if (list == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(list);
  }

  @Override
  public void updateAppName(String appName, String newName) {
    var changed = new AtomicBoolean(false);
    categoryAppLinks.values().forEach(list -> {
      if (list.contains(appName)) {
        list.remove(appName);
        list.add(newName);
        changed.set(true);
      }
    });
    if (changed.get()) {
      saveCategoryAppLinks();
    }
  }

  private void moveCategoryApps(String fullPath, String newFullPath) {
    if (fullPath.equals(newFullPath)) {
      return;
    }

    var list = categoryAppLinks.get(fullPath);
    if (list != null) {
      categoryAppLinks.remove(fullPath);
      categoryAppLinks.put(newFullPath, list);
      saveCategoryAppLinks();
    }
  }

  private void saveCategories() {
    var categoryFilePath = Paths.get(getWorkspace(), CATEGORY_FILE_NAME);
    var categoryPO = Trees.<AppCategoryDTO, AppCategoryPO>mapTree(categories, AppCategoryDTO::getChildren, (voNode, children) -> {
      var poNode = new AppCategoryPO();
      poNode.setName(voNode.getName());
      poNode.setOrder(voNode.getOrder());
      poNode.setIcon(voNode.getIcon());
      poNode.setChildren(children);
      return poNode;
    });
    Try.run(() -> Files.write(categoryFilePath, Json.toPrettyJsonString(categoryPO).getBytes()));
  }

  private void saveCategoryAppLinks() {
    var categoryAppLinksFilePath = Paths.get(getWorkspace(), CATEGORY_APP_LINK_FILE_NAME);
    Try.run(() -> Files.write(categoryAppLinksFilePath, Json.toPrettyJsonString(categoryAppLinks).getBytes()));
  }
}
