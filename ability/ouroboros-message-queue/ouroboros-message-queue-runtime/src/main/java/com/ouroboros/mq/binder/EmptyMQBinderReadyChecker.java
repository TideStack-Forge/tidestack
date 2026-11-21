package com.ouroboros.mq.binder;

import org.springframework.cloud.stream.binder.Binder;
import org.springframework.stereotype.Component;

@Component
public class EmptyMQBinderReadyChecker implements MQBinderReadyChecker {
  @Override
  public boolean isReady(String binderName, Binder<?, ?, ?> binder) {
    return false;
  }
}
