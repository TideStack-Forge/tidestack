package com.ouroboros.configuration.service.impl;

import java.util.Objects;

import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;

/**
 * @author liansz
 */
@Service
@DevelopModule("configuration")
@SuppressWarnings("unused")
public class ConfigurationMetadataDevelopServiceImpl extends BaseDevelopService<ConfigurationMetadata> {

  private static final String MODEL_FILE_DIRECTORY = "configuration";
  private static final String MODEL_FILE_EXTENSION = ".config.json";

  @Override
  protected String getModelDisplayName() {
    return "配置项";
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
  protected String getFullName(ConfigurationMetadata model) {
    return model.getKey();
  }

  /**
   * 验证模型基本信息
   *
   * @param model
   */
  @Override
  protected Try<Void> validateModel(ConfigurationMetadata model) {
    return Try.run(() -> {
      Objects.requireNonNull(model, "模型不能为空！");

      String key = model.getKey();
      Objects.requireNonNull(key, "配置键不能为空！");
    });
  }
}
