package com.ouroboros.dev.datasource.service.impl;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.datasource.dto.TableInfo;
import com.ouroboros.dev.datasource.model.AppDataSourceModel;
import com.ouroboros.dev.datasource.service.AppDataSourceService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

/**
 * @author lianshuze
 **/
@Service
public class AppDataSourceServiceImpl implements AppDataSourceService {
  private final PlatformDataSourceService platformDataSourceService;

  @Autowired
  private TypedDataModel<String, AppDataSourceModel> dataModel;

  public AppDataSourceServiceImpl(PlatformDataSourceService platformDataSourceService) {
    this.platformDataSourceService = platformDataSourceService;
  }

  @Override
  public Try<AppDataSourceModel> add(AppDataSourceModel model) {
    return Try.of(() -> getDataModel().insert(model).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException));
  }

  @Override
  public Try<Boolean> delete(String primaryKey) {
    return Try.of(() -> {
      var m = getDataModel();
      var c = m.delete(primaryKey).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      return c > 0;
    });
  }

  @Override
  public Try<Boolean> update(String primaryKey, AppDataSourceModel model) {
    return Try.of(() -> {
      var m = getDataModel();
      var c = m.update(primaryKey, model).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      return c > 0;
    });
  }

  @Override
  public Optional<AppDataSourceModel> getByPrimaryKey(String primaryKey) {
    var m = getDataModel();
    var result = m.get(primaryKey).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return Optional.ofNullable(result);
  }

  @Override
  public List<AppDataSourceModel> getByAppName(String appName) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);

    return query(Collections.singletonMap(Keyword.WHERE.toString(), where));
  }

  @Override
  public Optional<AppDataSourceModel> getByAppNameAndAppDataSourceName(String appName, String dataStationName) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);
    where.put("dataStationName", dataStationName);

    return query(Collections.singletonMap(Keyword.WHERE.toString(), where)).stream().findFirst();
  }

  @Override
  public Long count(Map<String, Object> where) {
    return getDataModel().count(where).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public List<AppDataSourceModel> query(Map<String, Object> statement) {
    return getDataModel().query(statement).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public List<AppDataSourceModel> getAll() {
    return query(Collections.emptyMap());
  }

  @Override
  public List<TableInfo> getTableInfo(String appName, String dataStationName, String envName) {
    var appDataSource = getByAppNameAndAppDataSourceName(appName, dataStationName);
    if (!appDataSource.isPresent()) {
      return Collections.emptyList();
    }

    var platformDataSourceName = appDataSource.get().getPlatformDataSourceName();
    return platformDataSourceService.getTables(platformDataSourceName, envName);
  }

  /**
   * 获取数据模型
   *
   * @return
   */
  private TypedDataModel<String, AppDataSourceModel> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
