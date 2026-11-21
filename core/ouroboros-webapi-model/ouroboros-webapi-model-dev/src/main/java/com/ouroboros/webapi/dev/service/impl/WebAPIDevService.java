package com.ouroboros.webapi.dev.service.impl;

import java.util.Objects;

import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.dev.model.LowcodeWebAPIModel;

@Service
@DevelopModule("webapi")
@SuppressWarnings("unused")
public class WebAPIDevService extends BaseDevelopService<LowcodeWebAPIModel> {

  static final String METADATA_FILE_HOME = "webapi";
  static final String MODEL_FILE_SUFFIX = ".webapi.json";

  @Override
  protected String getModelDisplayName() {
    return "WebAPI模型";
  }

  @Override
  protected String getModelFileDirectory() {
    return METADATA_FILE_HOME;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_SUFFIX;
  }

  @Override
  protected String getFullName(LowcodeWebAPIModel model) {
    return model.getFullName();
  }

  /**
   * 验证模型基本信息
   *
   * @param model
   */
  @Override
  protected Try<Void> validateModel(LowcodeWebAPIModel model) {
    return Try.run(() -> {
      Objects.requireNonNull(model, "模型不能为空！");

      String fullName = model.getFullName();
      Objects.requireNonNull(fullName, "WebAPI路径不能为空！");

      String method = model.getMethod();
      Objects.requireNonNull(method, "WebAPI请求方式不能为空！");

      String path = model.getPath();
      Objects.requireNonNull(path, "WebAPI路径不能为空！");

      WebAPIScope scope = model.getScope();
      Objects.requireNonNull(scope, "WebAPI作用域不能为空！");

      HandlerType handlerType = model.getHandlerType();
      Objects.requireNonNull(handlerType, "WebAPI处理类型不能为空！");
    });
  }
}
