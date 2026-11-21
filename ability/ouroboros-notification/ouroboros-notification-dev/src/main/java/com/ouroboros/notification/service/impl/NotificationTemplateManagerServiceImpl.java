package com.ouroboros.notification.service.impl;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.notification.template.DefaultNotificationTemplate;

/**
 * @author liansz
 */
@Service
@DevelopModule("notification-template")
public class NotificationTemplateManagerServiceImpl extends BaseDevelopService<DefaultNotificationTemplate> {

  private static final String METADATA_FILE_HOME = "notification-template";
  private static final String MODEL_FILE_SUFFIX = ".notification.json";

  @Override
  protected String getModelDisplayName() {
    return "通知模版";
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
  protected String getFullName(DefaultNotificationTemplate model) {
    return model.getName();
  }

  /**
   * 检查模板基本信息
   *
   * @param template 模板
   */
  @Override
  protected Try<Void> validateModel(DefaultNotificationTemplate template) {
    if (ObjectUtils.isEmpty(template)) {
      return Try.failure(new BusinessException("模版不能为空！"));
    }

    if (ObjectUtils.isEmpty(template.getName())) {
      return Try.failure(new BusinessException("名称不能为空！"));
    }

    if (ObjectUtils.isEmpty(template.getTitle())) {
      return Try.failure(new BusinessException("标题不能为空！"));
    }

    if (ObjectUtils.isEmpty(template.getChannel())) {
      return Try.failure(new BusinessException("渠道不能为空！"));
    }

    return Try.success(null);
  }
}
