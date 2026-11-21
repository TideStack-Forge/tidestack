package com.ouroboros.uimodel.dev.service.impl;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.uimodel.dev.model.LowcodeUIModel;

@Service
@DevelopModule("ui-model")
@SuppressWarnings("unused")
public class UIModelDevService extends BaseDevelopService<LowcodeUIModel> {
  final static Logger logger = LoggerFactory.getLogger(UIModelDevService.class);
  final static String MODEL_FILE_DIRECTORY = "ui-model";
  final static String MODEL_FILE_EXTENSION = ".ui-model.json";
  private static final String LEGACY_MODEL_FILE_EXTENSION = ".json";

  @Override
  protected String getModelDisplayName() {
    return "UI模型";
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
  protected List<String> getLegacyModelFileExtensions() {
    return Collections.singletonList(LEGACY_MODEL_FILE_EXTENSION);
  }

  @Override
  protected String getFullName(LowcodeUIModel model) {
    return model.getFullName();
  }

  @Override
  protected Try<Void> validateModel(LowcodeUIModel model) {
    if (StringUtils.isBlank(model.getFullName())) {
      return Try.failure(new BusinessException("UI模型名称不能为空"));
    }
    return Try.success(null);
  }

}
