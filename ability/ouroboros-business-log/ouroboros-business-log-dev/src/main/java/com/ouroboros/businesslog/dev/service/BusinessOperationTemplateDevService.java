package com.ouroboros.businesslog.dev.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.businesslog.DefaultBusinessOperationTemplate;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;

@Service
@DevelopModule("business-log-template")
public class BusinessOperationTemplateDevService extends BaseDevelopService<DefaultBusinessOperationTemplate> {

  private static final String MODEL_FILE_DIRECTORY = "business-log-template";
  private static final String MODEL_FILE_EXTENSION = ".business-log.json";

  @Override
  protected String getModelDisplayName() {
    return "业务日志模板";
  }

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_EXTENSION;
  }

  @Override
  protected String getFullName(DefaultBusinessOperationTemplate model) {
    return normalizeName(model == null ? null : model.getName());
  }

  @Override
  protected Try<Void> validateModel(DefaultBusinessOperationTemplate model) {
    if (model == null) {
      return Try.failure(new BusinessException("业务日志模板不能为空"));
    }

    normalizeModel(model);

    if (StringUtils.isBlank(model.getName())) {
      return Try.failure(new BusinessException("模板名称不能为空"));
    }
    if (StringUtils.isBlank(model.getHttpMethod())) {
      return Try.failure(new BusinessException("请求方法不能为空"));
    }
    if (StringUtils.isBlank(model.getUrl())) {
      return Try.failure(new BusinessException("URL 不能为空"));
    }
    if (StringUtils.isBlank(model.getModuleName())) {
      return Try.failure(new BusinessException("模块名不能为空"));
    }
    if (StringUtils.isBlank(model.getOperation())) {
      return Try.failure(new BusinessException("操作名不能为空"));
    }
    if (StringUtils.isBlank(model.getLevel())) {
      return Try.failure(new BusinessException("级别不能为空"));
    }

    return Try.success(null);
  }

  @Override
  public Try<DefaultBusinessOperationTemplate> create(DefaultBusinessOperationTemplate model, String commitMsg) {
    return validateUniqueness(model, null)
        .flatMap(ignore -> super.create(model, commitMsg));
  }

  @Override
  public Try<DefaultBusinessOperationTemplate> update(String fullName, DefaultBusinessOperationTemplate model) {
    return validateUniqueness(model, fullName)
        .flatMap(ignore -> super.update(fullName, model));
  }

  @Override
  protected Predicate<?> buildSearchFilter(Map<String, Object> searchParam) {
    String keyword = StringUtils.trimToEmpty((String) searchParam.get("keyword"));
    String httpMethod = StringUtils.upperCase(StringUtils.trimToEmpty((String) searchParam.get("httpMethod")));
    String moduleName = StringUtils.trimToEmpty((String) searchParam.get("moduleName"));

    return raw -> {
      DefaultBusinessOperationTemplate model = (DefaultBusinessOperationTemplate) raw;
      return (StringUtils.isBlank(keyword)
          || contains(model.getName(), keyword)
          || contains(buildTemplateKey(model), keyword)
          || contains(model.getModuleName(), keyword)
          || contains(model.getOperation(), keyword))
          && (StringUtils.isBlank(httpMethod) || StringUtils.equalsIgnoreCase(model.getHttpMethod(), httpMethod))
          && (StringUtils.isBlank(moduleName) || contains(model.getModuleName(), moduleName));
    };
  }

  protected Try<Void> validateUniqueness(DefaultBusinessOperationTemplate model, String currentFullName) {
    Try<Void> baseValidation = validateModel(model);
    if (baseValidation.isFailure()) {
      return baseValidation;
    }

    String normalizedCurrentFullName = normalizeName(currentFullName);
    String expectedTemplateKey = buildTemplateKey(model);

    List<DefaultBusinessOperationTemplate> existingTemplates = search(Collections.emptyMap()).stream()
        .filter(DefaultBusinessOperationTemplate.class::isInstance)
        .map(DefaultBusinessOperationTemplate.class::cast)
        .collect(Collectors.toList());

    boolean duplicatedName = existingTemplates.stream()
        .anyMatch(existing -> StringUtils.equals(normalizeName(existing.getName()), normalizeName(model.getName()))
            && !StringUtils.equals(normalizeName(existing.getName()), normalizedCurrentFullName));
    if (duplicatedName) {
      return Try.failure(new BusinessException("模板名称已存在: " + model.getName()));
    }

    boolean duplicatedTemplateKey = existingTemplates.stream()
        .anyMatch(existing -> StringUtils.equalsIgnoreCase(buildTemplateKey(existing), expectedTemplateKey)
            && !StringUtils.equals(normalizeName(existing.getName()), normalizedCurrentFullName));
    if (duplicatedTemplateKey) {
      return Try.failure(new BusinessException("相同 HTTP 方法和 URL 的业务日志模板已存在"));
    }

    return Try.success(null);
  }

  private boolean contains(String value, String keyword) {
    return StringUtils.containsIgnoreCase(StringUtils.defaultString(value), StringUtils.defaultString(keyword));
  }

  private void normalizeModel(DefaultBusinessOperationTemplate model) {
    model.setName(normalizeName(model.getName()));
    model.setHttpMethod(StringUtils.upperCase(StringUtils.trimToNull(model.getHttpMethod())));
    model.setUrl(StringUtils.trimToNull(model.getUrl()));
    model.setModuleName(StringUtils.trimToNull(model.getModuleName()));
    model.setOperation(StringUtils.trimToNull(model.getOperation()));
    model.setLevel(StringUtils.trimToNull(model.getLevel()));
    model.setBusinessKeyName(StringUtils.trimToNull(model.getBusinessKeyName()));
    model.setSummaryTemplate(StringUtils.trimToNull(model.getSummaryTemplate()));
  }

  private String normalizeName(String name) {
    return StringUtils.trimToNull(name);
  }

  private String buildTemplateKey(DefaultBusinessOperationTemplate model) {
    return buildTemplateKey(model.getHttpMethod(), model.getUrl());
  }

  private String buildTemplateKey(String httpMethod, String url) {
    return String.format("%s %s",
        StringUtils.upperCase(StringUtils.trimToEmpty(httpMethod)),
        StringUtils.trimToEmpty(url)
    ).trim();
  }
}
