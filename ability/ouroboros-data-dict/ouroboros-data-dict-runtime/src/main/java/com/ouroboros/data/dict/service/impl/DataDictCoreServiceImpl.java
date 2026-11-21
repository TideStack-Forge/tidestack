package com.ouroboros.data.dict.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.ouroboros.data.dict.service.DataDictCoreService;
import com.ouroboros.data.dict.DataDict;
import com.ouroboros.data.dict.pojo.AbstractDataDict;
import com.ouroboros.data.dict.pojo.FlatDataDict;
import com.ouroboros.data.dict.pojo.TreeDataDict;
import com.ouroboros.data.dict.service.model.DataDictModel;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

/**
 * @author liansz
 */
@Service
@CacheConfig(cacheNames = "ouroboros:data-dict")
public class DataDictCoreServiceImpl implements DataDictCoreService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private DataDictCoreServiceImpl self;

  @Autowired
  private TypedDataModel<String, DataDictModel> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  public List<DataDictModel> add(Collection<DataDictModel> models) throws OuroborosException {
    return getDataModel()
        .batchInsert(models)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean delete(Collection<String> ids) throws OuroborosException {
    return getDataModel()
        .delete(ids)
        .map(l -> l > 0)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean drop(String name) throws OuroborosException {
    Long count = getDataModel()
        .withoutPlugins("LogicalDelete")
        .delete(name)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return count > 0;
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean update(String name, DataDictModel model) throws OuroborosException {
    Long count = getDataModel()
        .update(name, model)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return count > 0;
  }

  @Override
  public Optional<? extends DataDict> getByName(String name) {
    try {
      return searchAll().stream()
          .filter(model -> Objects.equals(model.getName(), name))
          .findAny();
    } catch (Exception e) {
      logger.error("查询数据字典错误!", e);
      return Optional.empty();
    }
  }

  @Override
  public List<? extends DataDict> searchAll() {
    return self.doSearchAll()
        .stream()
        .map(DataDictCoreServiceImpl::toDataDict)
        .collect(Collectors.toList());
  }

  private static DataDict toDataDict(DataDictModel model) {
    AbstractDataDict dict = model.isTreeMode() ? new TreeDataDict() : new FlatDataDict();
    dict.setName(model.getName());
    dict.setTitle(model.getTitle());
    dict.setType(model.getType());
    dict.setGroup(model.getGroup());
    dict.setEditable(model.isEditable());
    dict.setTreeMode(model.isTreeMode());
    return dict;
  }

  @Cacheable(sync = true)
  public List<DataDictModel> doSearchAll() {
    return getDataModel()
        .query(new HashMap<>())
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  /**
   * 获取数据模型
   *
   * @return
   */
  private TypedDataModel<String, DataDictModel> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
