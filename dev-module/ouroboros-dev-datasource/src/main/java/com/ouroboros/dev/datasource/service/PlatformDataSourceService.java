package com.ouroboros.dev.datasource.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.dev.datasource.dto.TableInfo;
import com.ouroboros.dev.datasource.model.PlatformDataSourceModel;

/**
 * 平台数据源服务接口
 * 定义了平台数据源的增删改查等基本操作
 */
public interface PlatformDataSourceService {
  /**
   * 添加新的平台数据源
   * @param model 平台数据源模型对象
   * @return 返回操作结果，包含成功或失败的信息
   */
  Try<PlatformDataSourceModel> add(PlatformDataSourceModel model);

  /**
   * 根据主键删除平台数据源
   * @param primaryKey 数据源的主键
   * @return 返回操作结果，包含成功或失败的信息
   */
  Try<Boolean> delete(String primaryKey);

  /**
   * 更新平台数据源信息
   * @param primaryKey 数据源的主键
   * @param model 更新的平台数据源模型对象
   * @return 返回操作结果，包含成功或失败的信息
   */
  Try<Boolean> update(String primaryKey, PlatformDataSourceModel model);

  /**
   * 根据主键获取平台数据源
   * @param primaryKey 数据源的主键
   * @return 返回包含平台数据源对象的Optional，如果不存在则为空
   */
  Optional<PlatformDataSourceModel> getByPrimaryKey(String primaryKey);

  /**
   * 根据名称获取平台数据源
   * @param name 数据源的名称
   * @return 返回包含平台数据源对象的Optional，如果不存在则为空
   */
  Optional<PlatformDataSourceModel> getByName(String name);

  /**
   * 根据名称列表获取多个平台数据源
   * @param names 数据源名称列表
   * @return 返回匹配的数据源列表
   */
  List<PlatformDataSourceModel> getByNames(List<String> names);

  /**
   * 统计符合条件的平台数据源数量
   * @param where 查询条件
   * @return 返回符合条件的数量
   */
  Long count(Map<String, Object> where);

  /**
   * 查询平台数据源
   * @param statement 查询条件
   * @return 返回符合条件的数据源列表
   */
  List<PlatformDataSourceModel> query(Map<String, Object> statement);

  /**
   * 获取所有平台数据源
   * @return 返回所有平台数据源的列表
   */
  List<PlatformDataSourceModel> getAll();

  /**
   * 获取指定平台数据源的表信息
   * @param platformDataSourceName 平台数据源名称
   * @param envName 环境名称
   * @return 返回表信息列表
   */
  List<TableInfo> getTables(String platformDataSourceName, String envName);
}
