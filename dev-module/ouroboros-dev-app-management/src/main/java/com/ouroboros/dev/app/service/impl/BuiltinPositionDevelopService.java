package com.ouroboros.dev.app.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.organizationstructure.PositionDefinition;

@Service
@DevelopModule("builtin-position")
public class BuiltinPositionDevelopService extends BaseDevelopService<PositionDefinition> {
  static final String MODEL_FILE_DIRECTORY = "organization-structure/positions";

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return ".position.json";
  }

  @Override
  protected String getFullName(PositionDefinition model) {
    return model == null ? null : model.getKey();
  }

  @Override
  protected String getModelDisplayName() {
    return "内置岗位";
  }

  @Override
  protected Try<Void> validateModel(PositionDefinition model) {
    return Try.run(() -> {
      if (model == null) {
        throw new BusinessException("内置岗位不能为空");
      }
      if (StringUtils.isBlank(model.getKey())) {
        throw new BusinessException("岗位编码不能为空");
      }
      if (StringUtils.isBlank(model.getName())) {
        throw new BusinessException("岗位名称不能为空");
      }
    });
  }

  @Override
  public Try<PositionDefinition> update(String fullName, PositionDefinition model) {
    if (model == null || !StringUtils.equals(fullName, model.getKey())) {
      return Try.failure(new BusinessException("岗位编码不能在更新时变更: " + fullName));
    }
    return super.update(fullName, model);
  }
}
