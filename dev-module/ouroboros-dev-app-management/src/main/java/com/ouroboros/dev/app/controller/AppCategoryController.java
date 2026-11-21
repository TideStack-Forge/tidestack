package com.ouroboros.dev.app.controller;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.dev.app.model.AppCategoryDTO;
import com.ouroboros.dev.app.service.AppCategoryService;
import com.ouroboros.util.Trees;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/app/category")
@SuppressWarnings("unused")
public class AppCategoryController {
  @Autowired
  private AppCategoryService appCategoryService;

  @GetMapping("")
  public ItemsWebApiResult<AppCategoryDTO> listCategories(@RequestParam(name = "except", defaultValue = "") String except) {
    var categories = appCategoryService.listCategories();
    if (except.isEmpty() || categories.isFailure()) {
      return ItemsWebApiResult.of(categories);
    }
    var filteredCategories = Trees.filterTree(
        categories.get().stream()
            .map(AppCategoryDTO::clone)
            .collect(Collectors.toList()),
        node -> !except.equalsIgnoreCase(node.getName()),
        AppCategoryDTO::getChildren,
        AppCategoryDTO::setChildren);
    return ItemsWebApiResult.ok(filteredCategories);
  }

  @PostMapping("")
  public WebApiResult<Void> addCategory(@RequestBody AppCategoryDTO category) {
    if (category.getParentPath() == null) {
      category.setParentPath("");
    }
    category.setFullPath((category.getParentPath().isEmpty() ? "" : (category.getParentPath() + ".")) + category.getName());
    var result = appCategoryService.addCategory(category);
    if (result.isSuccess()) {
      return WebApiResult.ok();
    } else {
      return WebApiResult.error(result.getCause().getMessage());
    }
  }

  @PutMapping("/{fullPath}")
  public WebApiResult<Void> updateCategory(@PathVariable("fullPath") String fullPath, @RequestBody AppCategoryDTO newCategory) {
    if (newCategory.getParentPath().startsWith(fullPath)) {
      return WebApiResult.error("上级分类不能是当前分类或者当前分类的子分类");
    }
    newCategory.setFullPath((newCategory.getParentPath().isEmpty() ? "" : (newCategory.getParentPath() + ".")) + newCategory.getName());
    var result = appCategoryService.updateCategory(fullPath, newCategory);
    if (result.isSuccess()) {
      return WebApiResult.ok();
    } else {
      return WebApiResult.error(result.getCause().getMessage());
    }
  }

  @DeleteMapping("/{fullPath}")
  public WebApiResult<Void> deleteCategory(@PathVariable("fullPath") String fullPath) {
    var result = appCategoryService.deleteCategory(fullPath);
    return WebApiResult.of(result);
  }
}
