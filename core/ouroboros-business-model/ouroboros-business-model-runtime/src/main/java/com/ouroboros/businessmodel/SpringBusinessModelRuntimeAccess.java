package com.ouroboros.businessmodel;

import org.springframework.beans.BeansException;

import com.ouroboros.businessmodel.action.BusinessActionRuntime;
import com.ouroboros.businessmodel.event.BusinessEventPort;
import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.exception.BusinessException;

/** Stateless SPI bridge; invocation state remains owned by the runtime ports. */
public final class SpringBusinessModelRuntimeAccess implements BusinessModelRuntimeAccess {
  @Override
  public BusinessActionRuntime actions() {
    return requiredBean(BusinessActionRuntime.class);
  }

  @Override
  public BusinessEventPort events() {
    return requiredBean(BusinessEventPort.class);
  }

  private static <T> T requiredBean(Class<T> type) {
    try {
      return SpringContextUtil.getBean(type);
    } catch (BeansException | IllegalStateException failure) {
      throw new BusinessException("业务模型运行时尚未就绪：" + type.getSimpleName(), failure);
    }
  }
}
