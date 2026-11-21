package com.ouroboros.businesslog.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.ouroboros.businesslog.BusinessOperationTemplateCenter;
import com.ouroboros.businesslog.BusinessOperationTemplateSource;
import com.ouroboros.businesslog.model.BusinessOperationTemplateModel;
import com.ouroboros.businesslog.service.BusinessOperationTemplateQueryService;
import com.ouroboros.businesslog.service.model.BusinessOperationTemplateSourceView;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.web.PagedResult;

/**
 * 合并业务日志模板来源后的查询服务。
 */
@Service
public class BusinessOperationTemplateQueryServiceImpl implements BusinessOperationTemplateQueryService {

  @Override
  public PagedResult<BusinessOperationTemplateSourceView> search(String keyword,
                                                                 String source,
                                                                 String httpMethod,
                                                                 String moduleName,
                                                                 int page,
                                                                 int perPage) {
    List<BusinessOperationTemplateSourceView> filtered = new ArrayList<>(BusinessOperationTemplateCenter.getAllTemplates().values());

    if (StringUtils.isNotBlank(keyword)) {
      filtered = filtered.stream()
          .filter(view -> contains(view.getName(), keyword)
              || contains(view.getTemplateKey(), keyword)
              || contains(view.getModuleName(), keyword)
              || contains(view.getOperation(), keyword))
          .collect(Collectors.toList());
    }

    if (StringUtils.isNotBlank(source)) {
      Optional<BusinessOperationTemplateSource> expectedSource = parseSource(source);
      filtered = filtered.stream()
          .filter(view -> expectedSource.map(value -> value == view.getSource()).orElse(true))
          .collect(Collectors.toList());
    }

    if (StringUtils.isNotBlank(httpMethod)) {
      filtered = filtered.stream()
          .filter(view -> contains(view.getHttpMethod(), httpMethod))
          .collect(Collectors.toList());
    }

    if (StringUtils.isNotBlank(moduleName)) {
      filtered = filtered.stream()
          .filter(view -> contains(view.getModuleName(), moduleName))
          .collect(Collectors.toList());
    }

    int safePage = Math.max(page, 1);
    int safePerPage = Math.max(perPage, 1);
    int startIndex = Math.min((safePage - 1) * safePerPage, filtered.size());
    int endIndex = Math.min(startIndex + safePerPage, filtered.size());

    return PagedResult.of((long) filtered.size(), filtered.subList(startIndex, endIndex));
  }

  @Override
  public Optional<BusinessOperationTemplateSourceView> getDetail(String templateKey) {
    return Optional.ofNullable(BusinessOperationTemplateCenter.getAllTemplates().get(StringUtils.trimToEmpty(templateKey)));
  }

  @Override
  public BusinessOperationTemplateModel buildOverrideFromSource(String templateKey) {
    BusinessOperationTemplateSourceView sourceView = getDetail(templateKey)
        .orElseThrow(() -> new PlatformException("未找到指定业务日志模板"));

    BusinessOperationTemplateModel model = new BusinessOperationTemplateModel();
    model.setHttpMethod(sourceView.getHttpMethod());
    model.setUrl(sourceView.getUrl());
    model.setModuleName(sourceView.getModuleName());
    model.setOperation(sourceView.getOperation());
    model.setLevel(sourceView.getLevel());
    model.setBusinessKeyName(sourceView.getBusinessKeyName());
    model.setSummaryTemplate(sourceView.getSummaryTemplate());
    model.setSensitiveFields(sourceView.getSensitiveFields() == null
        ? Collections.emptyList()
        : new ArrayList<>(sourceView.getSensitiveFields()));
    model.setShouldSaveResponseBody(sourceView.isShouldSaveResponseBody());
    return model;
  }

  private boolean contains(String rawValue, String keyword) {
    return StringUtils.containsIgnoreCase(StringUtils.defaultString(rawValue), StringUtils.defaultString(keyword));
  }

  private Optional<BusinessOperationTemplateSource> parseSource(String source) {
    try {
      return Optional.of(BusinessOperationTemplateSource.valueOf(source.trim().toUpperCase(Locale.ROOT)));
    } catch (Exception parseFailure) {
      return Optional.empty();
    }
  }
}
