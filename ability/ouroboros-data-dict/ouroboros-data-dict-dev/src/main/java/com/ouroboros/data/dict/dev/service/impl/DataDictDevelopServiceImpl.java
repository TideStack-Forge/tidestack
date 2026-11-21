package com.ouroboros.data.dict.dev.service.impl;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.data.dict.DataDict;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;

@Service
@DevelopModule("data-dict")
public class DataDictDevelopServiceImpl extends BaseDevelopService<DataDict> {

  private static final String MODULE_HOME_PATH = "data-dict";
  private static final String MODULE_FILE_EXTENSION = ".dict.json";
  private static final String LEGACY_MODULE_FILE_EXTENSION = ".json";

  @Override
  protected String getModelDisplayName() {
    return "字典";
  }

  @Override
  protected String getModelFileDirectory() {
    return MODULE_HOME_PATH;
  }

  @Override
  protected String getModelFileExtension() {
    return MODULE_FILE_EXTENSION;
  }

  @Override
  protected List<String> getLegacyModelFileExtensions() {
    return Collections.singletonList(LEGACY_MODULE_FILE_EXTENSION);
  }

  @Override
  protected String getFullName(DataDict model) {
    return model.getName();
  }

  @Override
  protected Try<Void> validateModel(DataDict model) {
    if (model == null) {
      return Try.failure(new BusinessException(-1, "字典数据不完整"));
    }
    if (StringUtils.isBlank(model.getName())) {
      return Try.failure(new BusinessException(-1, "字典名称不能为空"));
    }
    return Try.success(null);
  }
}
