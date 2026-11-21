package com.ouroboros.fieldtype.dev.service;

import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.util.Json;

/**
 * @Author: daincly
 **/
@Service
@DevelopModule("field")
public class FieldTypeManagerServiceImpl extends BaseDevelopService<Map<String, Object>> {

  private static final String MODEL_FILE_DIRECTORY = "field-types";
  private static final String MODEL_FILE_SUFFIX = ".field-type.json";

  @Override
  protected String getModelDisplayName() {
    return "字段类型";
  }

  @Override
  public Try<Map<String, Object>> buildModelFromFileContent(String fileContent) {
    return Json.tryToMap(fileContent);
  }

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_SUFFIX;
  }

  @Override
  protected String getFullName(Map<String, Object> model) {
    return model.get("name").toString();
  }

  /**
   * 检查基本信息
   *
   * @param metadata 元数据
   */
  @Override
  protected Try<Void> validateModel(Map<String, Object> model) {
    if (ObjectUtils.isEmpty(model)) {
      return Try.failure(new BusinessException("字段类型定义不能为空！"));
    }

    if (ObjectUtils.isEmpty(model.get("name"))) {
      return Try.failure(new BusinessException("名称不能为空！"));
    }

    if (ObjectUtils.isEmpty(model.get("label"))) {
      return Try.failure(new BusinessException("标签不能为空！"));
    }

    return Try.success(null);
  }

}
