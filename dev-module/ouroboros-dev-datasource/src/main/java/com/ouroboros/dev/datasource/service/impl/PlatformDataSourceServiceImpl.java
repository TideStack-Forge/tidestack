package com.ouroboros.dev.datasource.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.datasource.dto.TableInfo;
import com.ouroboros.dev.datasource.model.DataSourceDefine;
import com.ouroboros.dev.datasource.model.PlatformDataSourceModel;
import com.ouroboros.dev.datasource.service.DataSourceMetadataService;
import com.ouroboros.dev.datasource.service.PlatformDataSourceService;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

/**
 * @author lianshuze
 **/
@Service
public class PlatformDataSourceServiceImpl implements PlatformDataSourceService {

  List<DataSourceMetadataService> dataSourceMetadataServices;

  @Autowired
  private TypedDataModel<String, PlatformDataSourceModel> dataModel;

  public PlatformDataSourceServiceImpl(ObjectProvider<DataSourceMetadataService> serviceProvider) {
    this.dataSourceMetadataServices = serviceProvider.stream().collect(Collectors.toList());
  }

  @Override
  public Try<PlatformDataSourceModel> add(PlatformDataSourceModel model) {
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
  public Try<Boolean> update(String primaryKey, PlatformDataSourceModel model) {
    return Try.of(() -> {
      var m = getDataModel();
      var c = m.update(primaryKey, model).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      return c > 0;
    });
  }

  @Override
  public Optional<PlatformDataSourceModel> getByPrimaryKey(String primaryKey) {
    var m = getDataModel();
    var result = m.get(primaryKey).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return Optional.ofNullable(result);
  }

  @Override
  public Optional<PlatformDataSourceModel> getByName(String name) {
    Map<String, Object> statement = Collections.singletonMap(Keyword.WHERE.toString(), Collections.singletonMap("name", name));
    return query(statement).stream().findFirst();
  }

  @Override
  public List<PlatformDataSourceModel> getByNames(List<String> names) {
    Map<String, Object> statement = Collections.singletonMap(Keyword.WHERE.toString(), Collections.singletonMap("name", names));
    return query(statement);
  }

  @Override
  public Long count(Map<String, Object> where) {
    return getDataModel().count(where).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public List<PlatformDataSourceModel> query(Map<String, Object> statement) {
    return getDataModel().query(statement).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public List<PlatformDataSourceModel> getAll() {
    return query(Collections.emptyMap());
  }

  @Override
  public List<TableInfo> getTables(String platformDataSourceName, String envName) {
    var dataSource = getByName(platformDataSourceName);
    if (!dataSource.isPresent()) {
      return Collections.emptyList();
    }

    var defineMap = dataSource.get().getDefinition().get(envName);
    if (defineMap == null) {
      return Collections.emptyList();
    }

    var define = new DataSourceDefine(defineMap);
    var metaService = dataSourceMetadataServices.stream()
        .filter(s -> s.isSupported(define))
        .findFirst();

    if (!metaService.isPresent()) {
      return Collections.emptyList();
    }

    var service = metaService.get();
    try (var metadata = service.getMetadata(define)) {
      return metadata.getAllTables().stream()
          .map(table -> new TableInfo()
              .setName(table.getName())
              .setRemarks(table.getRemarks()))
          .collect(Collectors.toList());
    }
    catch (Exception e) {
      return Collections.emptyList();
    }
  }

  /**
   * 获取数据模型
   *
   * @return
   */
  private TypedDataModel<String, PlatformDataSourceModel> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
