package com.ouroboros.dev.datasource.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.vavr.control.Try;

import com.ouroboros.dev.datasource.dto.TableInfo;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;

/**
 * 数据源服务接口，定义了数据源相关的操作方法
 * 提供了增删改查、统计查询等功能
 */
public interface AppDataSourceService {
  /**
   * 添加数据源
   * @param model 数据源模型对象
   * @return 返回Try类型，包含操作结果或异常
   */
  Try<AppDataSourceModel> add(AppDataSourceModel model);

  /**
   * 根据主键删除数据源
   * @param primaryKey 数据源主键
   * @return 返回Try类型，包含操作结果或异常
   */
  Try<Boolean> delete(String primaryKey);

  /**
   * 更新数据源信息
   * @param primaryKey 数据源主键
   * @param model 更新的数据源模型对象
   * @return 返回Try类型，包含操作结果或异常
   */
  Try<Boolean> update(String primaryKey, AppDataSourceModel model);

  /**
   * 根据主键获取数据源
   * @param primaryKey 数据源主键
   * @return 返回Optional类型的数据源模型对象，可能为空
   */
  Optional<AppDataSourceModel> getByPrimaryKey(String primaryKey);

  /**
   * 根据应用名称获取数据源列表
   * @param appName 应用名称
   * @return 返回数据源模型对象列表
   */
  List<AppDataSourceModel> getByAppName(String appName);

  /**
   * 根据应用名称和数据源名称获取数据源
   * @param appName 应用名称
   * @param appDataSourceName 数据源名称
   * @return 返回Optional类型的数据源模型对象，可能为空
   */
  Optional<AppDataSourceModel> getByAppNameAndAppDataSourceName(String appName, String appDataSourceName);

  /**
   * 统计符合条件的数据源数量
   * @param where 查询条件映射
   * @return 返回符合条件的数据源数量
   */
  Long count(Map<String, Object> where);

  /**
   * 查询符合条件的数据源列表
   * @param statement 查询条件映射
   * @return 返回数据源模型对象列表
   */
  List<AppDataSourceModel> query(Map<String, Object> statement);

  /**
   * 获取所有数据源
   * @return 返回所有数据源模型对象列表
   */
  List<AppDataSourceModel> getAll();

  /**
   * 获取数据表信息
   * @param appName 应用名称
   * @param dataStationName 数据源名称
   * @param envName 环境名称
   * @return 返回表信息列表
   */
  List<TableInfo> getTableInfo(String appName, String dataStationName, String envName);
}
