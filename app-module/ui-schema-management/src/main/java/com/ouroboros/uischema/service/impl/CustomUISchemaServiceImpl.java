package com.ouroboros.uischema.service.impl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.uischema.model.CustomUISchema;
import com.ouroboros.uischema.service.CustomUISchemaService;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 **/
@Service
@CacheConfig(cacheNames = "ouroboros:custom-ui-schema")
public class CustomUISchemaServiceImpl implements CustomUISchemaService {

  @Autowired
  private CustomUISchemaServiceImpl self;

  @Autowired
  private TypedDataModel<String, CustomUISchema> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  public List<CustomUISchema> add(Collection<CustomUISchema> schemas, String operatedBy) throws OuroborosException {
    LocalDateTime now = LocalDateTime.now();
    List<CustomUISchema> models = schemas.stream().map(user -> {
      var model = new CustomUISchema(user);
      model.setCreatedBy(operatedBy);
      model.setCreatedAt(now);

      return model;
    }).collect(Collectors.toList());

    var result = getDataModel().batchInsert(models);
    if (result.isSuccess()) {
      return result.get();
    }
    throw OuroborosExceptionMapper.toOuroborosException(result.getCause());
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean delete(String id, String operatedBy) throws OuroborosException {
    var dataModel = getDataModel();
    var result = dataModel.delete(id);
    if (result.isSuccess()) {
      return result.get() > 0;
    }
    throw OuroborosExceptionMapper.toOuroborosException(result.getCause());
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean update(String id, CustomUISchema schema, String operatedBy) throws OuroborosException {
    var model = new CustomUISchema(schema);
    model.setUpdatedBy(operatedBy);
    model.setUpdatedAt(LocalDateTime.now());

    Map<String, Object> data = Maps.fromBean(model)
        .entrySet().stream()
        .filter(entry -> entry.getValue() != null && entry.getKey() != null)
        .collect(() -> new TreeMap<>(String::compareToIgnoreCase), (m, e) -> m.put(e.getKey(), e.getValue()), TreeMap::putAll);
    Maps.remove(data, "createdBy", "createdAt", "deletedBy", "deletedAt", "deleted");

    var m = getDataModel();
    var result = m.update(id, data);
    if (result.isSuccess()) {
      return result.get() > 0;
    }
    throw OuroborosExceptionMapper.toOuroborosException(result.getCause());
  }

  @Override
  public CustomUISchema getById(String id) throws OuroborosException {
    var schemas = self.searchAll();
    return schemas.stream().filter(schema -> Objects.equals(schema.getId(), id)).findAny().orElse(null);
  }

  @Override
  @Cacheable(sync = true)
  public List<CustomUISchema> searchAll() throws OuroborosException {
    var model = getDataModel();
    var result = model.query(new HashMap<>());
    if (result.isSuccess()) {
      return result.get();
    }
    throw OuroborosExceptionMapper.toOuroborosException(result.getCause());
  }

  /**
   * 获取数据模型
   *
   * @return
   */
  private TypedDataModel<String, CustomUISchema> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
