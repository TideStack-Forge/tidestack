package com.ouroboros.dev.app.service;

import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppCategoryDTO;

/**
 * 应用分类服务接口，定义了应用分类相关的操作方法
 * 提供了分类的增删改查、应用管理等功能
 */
public interface AppCategoryService {

  /**
   * 添加新的应用分类
   *
   * @param category 包含分类信息的值对象
   * @return 返回Try类型，表示操作可能成功或失败
   */
  Try<Void> addCategory(AppCategoryDTO category);

  /**
   * 删除指定路径的应用分类
   *
   * @param fullPath 要删除的分类的完整路径
   * @return 返回Try类型，表示操作可能成功或失败
   */
  Try<Void> deleteCategory(String fullPath);

  /**
   * 更新指定路径的应用分类信息
   *
   * @param fullPath    要更新的分类的完整路径
   * @param newCategory 包含新分类信息的值对象
   * @return 返回Try类型，表示操作可能成功或失败
   */
  Try<Void> updateCategory(String fullPath, AppCategoryDTO newCategory);

  /**
   * 获取所有应用分类列表
   *
   * @return 返回包含所有分类信息的列表
   */
  Try<List<AppCategoryDTO>> listCategories();

  /**
   * 向指定分类中添加应用
   *
   * @param fullPath 目标分类的完整路径
   * @param appName  要添加的应用名称
   */
  void addApp(String fullPath, String appName);

  /**
   * 从指定分类中移除应用
   *
   * @param fullPath 源分类的完整路径
   * @param appName  要移除的应用名称
   */
  void removeApp(String fullPath, String appName);

  /**
   * 将应用移动到新的分类
   *
   * @param fullPath    源分类的完整路径
   * @param appName     要移动的应用名称
   * @param newFullPath 目标分类的完整路径
   */
  void moveApp(String fullPath, String appName, String newFullPath);

  /**
   * 获取指定分类下的所有应用
   *
   * @param fullPath 分类的完整路径
   * @return 返回包含应用名称的列表
   */
  List<String> getApps(String fullPath);

  /**
   * 更新应用名称
   *
   * @param appName 原应用名称
   * @param newName 新的应用名称
   */
  void updateAppName(String appName, String newName);

}
