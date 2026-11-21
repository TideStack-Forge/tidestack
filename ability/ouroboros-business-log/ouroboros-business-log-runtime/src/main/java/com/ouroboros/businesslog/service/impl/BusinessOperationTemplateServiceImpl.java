package com.ouroboros.businesslog.service.impl;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.businesslog.BusinessOperationTemplateCenter;
import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;
import com.ouroboros.businesslog.service.BusinessOperationTemplateService;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;

/**
 * @author liansz
 **/
@Service
@CacheConfig(cacheNames = "ouroboros:business-operation-template")
public class BusinessOperationTemplateServiceImpl implements BusinessOperationTemplateService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private BusinessOperationTemplateServiceImpl self;

  @Autowired
  private TypedDataModel<String, BusinessOperationTemplateModel> dataModel;

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public BusinessOperationTemplateModel add(BusinessOperationTemplateModel model) {
    normalizeModel(model);
    ensureUniqueTemplate(model, null);
    return getDataModel().insert(model)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public List<BusinessOperationTemplateModel> add(Collection<BusinessOperationTemplateModel> models) {
    return getDataModel().batchInsert(models)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean delete(String id) {
    return getDataModel().delete(id)
        .map(r -> r > 0)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  @CacheEvict(allEntries = true)
  @Transactional
  public Boolean update(String id, BusinessOperationTemplateModel model) {
    normalizeModel(model);
    ensureUniqueTemplate(model, id);
    return getDataModel().update(id, model)
        .map(r -> r > 0)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  @Override
  public Optional<BusinessOperationTemplateModel> getById(String id) {
    return self.searchAll().stream().filter(model ->
        Objects.equals(model.getId(), id)
    ).findAny();
  }

  @Override
  public Optional<BusinessOperationTemplateModel> getByHttpMethodAndUrl(String httpMethod, String url) {
    String expectedTemplateKey = BusinessOperationTemplateCenter.buildTemplateKey(httpMethod, url);
    return self.searchAll().stream()
        .filter(model -> StringUtils.equalsIgnoreCase(
            BusinessOperationTemplateCenter.buildTemplateKey(model),
            expectedTemplateKey
        ))
        .findAny();
  }

  @Override
  @Cacheable(sync = true)
  public List<BusinessOperationTemplateModel> searchAll() {
    try {
      return getDataModel().query(new HashMap<>())
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (Exception e) {
      logger.error("查询业务操作模版数据错误!", e);
      return Collections.emptyList();
    }
  }

  /**
   * 获取数据模型
   *
   * @return
   */
  protected TypedDataModel<String, BusinessOperationTemplateModel> getDataModel() {
    return dataModel;
  }

  private void ensureUniqueTemplate(BusinessOperationTemplateModel model, String currentId) {
    if (model == null || StringUtils.isAnyBlank(model.getHttpMethod(), model.getUrl())) {
      return;
    }

    Optional<BusinessOperationTemplateModel> duplicated = getByHttpMethodAndUrl(model.getHttpMethod(), model.getUrl())
        .filter(existing -> !Objects.equals(existing.getId(), currentId));

    if (duplicated.isPresent()) {
      throw new BusinessException("相同 HTTP 方法和 URL 的业务日志模板已存在");
    }
  }

  private void normalizeModel(BusinessOperationTemplateModel model) {
    if (model == null) {
      return;
    }
    model.setHttpMethod(StringUtils.upperCase(StringUtils.trimToNull(model.getHttpMethod())));
    model.setUrl(StringUtils.trimToNull(model.getUrl()));
    model.setModuleName(StringUtils.trimToNull(model.getModuleName()));
    model.setOperation(StringUtils.trimToNull(model.getOperation()));
    model.setLevel(StringUtils.trimToNull(model.getLevel()));
    model.setBusinessKeyName(StringUtils.trimToNull(model.getBusinessKeyName()));
    model.setSummaryTemplate(StringUtils.trimToNull(model.getSummaryTemplate()));
    model.setSensitiveFields(model.getSensitiveFields() == null
        ? Collections.emptyList()
        : new ArrayList<>(model.getSensitiveFields()));
    model.setShouldSaveResponseBody(Boolean.TRUE.equals(model.getShouldSaveResponseBody()));
  }
}
