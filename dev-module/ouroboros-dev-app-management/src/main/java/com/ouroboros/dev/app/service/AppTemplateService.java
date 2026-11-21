package com.ouroboros.dev.app.service;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AppTemplateDTO;
import com.ouroboros.model.AppInfo;

/**
 * 应用模板服务接口，提供应用模板相关的操作方法
 */
public interface AppTemplateService {

  /**
   * 获取所有应用模板
   * @return 包含所有应用模板的列表
   */
  List<AppTemplateDTO> getAll();

  /**
   * 根据模板名称获取单个应用模板
   * @param name 模板名称
   * @return 包含应用模板的Optional对象，如果不存在则为空
   */
  Optional<AppTemplateDTO> getOne(String name);

  /**
   * 使用指定模板创建应用
   * @param appInfo 要创建的应用基本信息
   * @param templateName 使用的模板名称
   * @return Try对象，包含操作结果（成功或失败）
   */
  Try<Void> createAppByTemplate(AppInfo appInfo, String templateName);

  /**
   * 从现有应用创建模板
   * @param template 要创建的模板名称
   * @param appName 作为模板来源的应用名称
   * @return Try对象，包含操作结果（成功或失败）
   */
  Try<Void> createTemplateFromApp(AppTemplateDTO template, String appName);

  /**
   * 从现有应用的指定版本创建模板
   * @param template 要创建的模板描述对象
   * @param appName 作为模板来源的应用名称
   * @param appVersion 作为模板来源的应用版本
   * @return Try对象，包含操作结果（成功或失败）
   */
  Try<Void> createTemplateFromApp(AppTemplateDTO template, String appName, String appVersion);

  /**
   * 从文件导入模板
   * @param templateName 要导入的模板名称
   * @param templateStream 模板文件流
   * @return Try对象，包含操作结果（成功或失败）
   */
  Try<Void> importTemplate(String templateName, InputStream templateStream);

  /**
   * 删除模板
   * @param templateName 要删除的模板名称
   * @return Try对象，包含操作结果（成功或失败）
   */
  Try<Void> deleteTemplate(String templateName);

  Try<InputStream> exportTemplate(String templateName);
}
