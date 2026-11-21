package com.ouroboros.mq.dev.service.impl;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.vavr.control.Try;

import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.mq.binding.MQBindingProperties;
import com.ouroboros.mq.dev.model.MQBindingModel;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

@Component
@DevelopModule("mq-binding")
public class MQBindingDevelopService extends BaseDevelopService<MQBindingModel> {
  private static final String MODEL_DIRECTORY = "mq-binding";
  private static final String MODEL_FILE_EXTENSION = ".mq.json";

  @Override
  protected String getModelFileDirectory() {
    return MODEL_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_EXTENSION;
  }

  @Override
  protected String getModelDisplayName() {
    return "MQ绑定";
  }

  @Override
  protected String getFullName(MQBindingModel model) {
    return model.getBindingName();
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  public Try<MQBindingModel> buildModelFromFileContent(String fileContent) {
    return Json.tryToBean(MQBindingProperties.class, fileContent)
        .map(MQBindingModel::new);
  }

  @Override
  public Try<MQBindingModel> buildModelFromRequestBody(String requestBody) {
    return Json.tryToBean(MQBindingModel.class, requestBody);
  }

  @Override
  protected Try<String> getPermanentJson(MQBindingModel model) {
    return Json.tryToPrettyJsonString(model.toMQBindingProperties());
  }

  @Override
  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> {
      var model = buildModelFromFileContent(file.getContent());
      return model.map(m -> new ModelFileMetaWrapper<>(m, file));
    };
  }

  @Override
  protected Predicate<ModelFileMetaWrapper<MQBindingModel>> buildSearchFilter(Map<String, Object> searchParam) {
    var keyword = searchParam.getOrDefault("keyword", "").toString().toLowerCase();
    var bindingType = searchParam.getOrDefault("bindingType", "").toString().toLowerCase();
    if (StringUtils.isBlank(keyword) && StringUtils.isBlank(bindingType)) {
      return m -> true;
    }
    return m -> {
      var model = m.getModel();
      var isKeywordMatch = keyword.isEmpty()
          || StringUtils.containsIgnoreCase(model.getBindingName(), keyword)
          || StringUtils.containsIgnoreCase(model.getDescription(), keyword);
      var isTypeMatch = bindingType.isEmpty() || bindingType.equalsIgnoreCase(model.getBindingType());
      return isKeywordMatch && isTypeMatch;
    };
  }
}
