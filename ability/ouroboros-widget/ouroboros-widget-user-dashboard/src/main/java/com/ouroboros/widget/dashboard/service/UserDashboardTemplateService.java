package com.ouroboros.widget.dashboard.service;

import java.util.List;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.widget.dashboard.model.UserDashboardTemplateModel;

/**
 * 用户仪表盘模板服务接口
 * 该接口定义了用户仪表盘模板的增删改查操作
 */
public interface UserDashboardTemplateService {
  /**
   * 添加用户仪表盘模板
   * @param template 用户仪表盘模板模型
   * @return 返回操作结果，包含添加成功的模板或异常信息
   */
  Try<UserDashboardTemplateModel> addTemplate(UserDashboardTemplateModel template);
  /**
   * 更新用户仪表盘模板
   * @param id 要更新的模板ID
   * @param template 更新的模板数据
   * @return 返回操作结果，包含更新后的模板或异常信息
   */
  Try<UserDashboardTemplateModel> updateTemplate(String id, UserDashboardTemplateModel template);
  /**
   * 删除用户仪表盘模板
   * @param id 要删除的模板ID
   * @return 返回操作结果，成功则返回空，失败则返回异常信息
   */
  Try<Void> deleteTemplate(String id);
  /**
   * 根据ID获取用户仪表盘模板
   * @param id 模板ID
   * @return 返回Optional包装的模板对象，如果不存在则为空
   */
  Optional<UserDashboardTemplateModel> getTemplate(String id);
  /**
   * 获取所有用户仪表盘模板
   * @return 返回所有模板的列表
   */
  List<UserDashboardTemplateModel> getAllTemplates();

  Optional<UserDashboardTemplateModel> getDefaultTemplate();
}
