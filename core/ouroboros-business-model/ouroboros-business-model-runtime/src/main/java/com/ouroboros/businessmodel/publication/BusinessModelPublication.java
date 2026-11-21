package com.ouroboros.businessmodel.publication;

import java.util.function.Supplier;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;

/** Business entry points share the existing application's complete refresh boundary. */
public final class BusinessModelPublication {
  private BusinessModelPublication() {
  }

  public static <T> T withPublishedContext(Supplier<T> execution) {
    try {
      return OuroborosContextRefreshLifecycle.withStableContext(execution);
    } catch (OuroborosContextRefreshLifecycle.PublishedContextUnavailableException failure) {
      throw new BusinessException("业务模型发布快照不可用", failure);
    }
  }
}
