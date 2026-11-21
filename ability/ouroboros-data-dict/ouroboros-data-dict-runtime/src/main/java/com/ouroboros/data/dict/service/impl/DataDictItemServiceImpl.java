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

import com.ouroboros.data.dict.service.DataDictItemService;
import com.ouroboros.data.dict.service.model.DataDictItemModel;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

/**
 * @author liansz
 */
@Service
@CacheConfig(cacheNames = "ouroboros:data-dict-item")
public class DataDictItemServiceImpl implements DataDictItemService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private DataDictItemServiceImpl self;

  @Autowired
  private TypedDataModel<String, DataDictItemModel> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  public List<DataDictItemModel> add(List<DataDictItemModel> models) throws OuroborosException {
    return getDataModel().batchInsert(models).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public Long deleteByDict(String dict) throws OuroborosException {
    Map<String, Object> where = new HashMap<>();
    where.put("dict", dict);

    return getDataModel().delete(where).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public Long deleteByDictCodes(String dict, Collection<String> codes) throws OuroborosException {
    Map<String, Object> where = new HashMap<>();
    where.put("dict", dict);
    where.put("code", codes);

    return getDataModel().delete(where).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public Long dropByDict(String dict) throws OuroborosException {
    Map<String, Object> where = new HashMap<>();
    where.put("dict", dict);

    return getDataModel().withoutPlugins("LogicalDelete").delete(where).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public Long dropByDictCodes(String dict, Collection<String> codes) throws OuroborosException {
    Map<String, Object> where = new HashMap<>();
    where.put("dict", dict);
    where.put("code", codes);

    return getDataModel().withoutPlugins("LogicalDelete").delete(where).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  public Boolean update(String dict, String code, DataDictItemModel model) throws OuroborosException {
    TypedDataModel<String, DataDictItemModel> dataModel = getDataModel();
    Long count = getByDictCode(dict, code)
        .map(o -> {
          try {
            return dataModel.update(o.getId(), model).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
          } catch (OuroborosException ex) {
            throw ex;
          }
        })
        .orElse(0L);
    return count > 0;
  }

  @Override
  public Optional<DataDictItemModel> getByDictCode(String dict, String code) {
    return searchAll().stream()
        .filter(m -> Objects.equals(m.getDict(), dict))
        .filter(m -> Objects.equals(m.getCode(), code))
        .findAny();
  }

  @Override
  public List<DataDictItemModel> searchListByDict(String dict) {
    return searchAll().stream()
        .filter(m -> Objects.equals(m.getDict(), dict))
        .collect(Collectors.toList());
  }

  @Override
  public List<DataDictItemModel> searchAll() {
    return self.doSearchAll().stream().map(DataDictItemModel::new).collect(Collectors.toList());
  }

  @Cacheable(sync = true)
  public List<DataDictItemModel> doSearchAll() {
    try {
      return getDataModel().query(Collections.emptyMap()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (OuroborosException e) {
      logger.error("查询数据字典项错误!", e);
      return Collections.emptyList();
    }
  }

  /**
   * 获取数据模型
   *
   * @return
   */
  private TypedDataModel<String, DataDictItemModel> getDataModel() throws OuroborosException {
    return dataModel;
  }
}
