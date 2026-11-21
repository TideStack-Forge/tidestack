package com.ouroboros.mq.binder;

import org.springframework.cloud.stream.binder.Binder;

@FunctionalInterface
public interface MQBinderReadyChecker {
  boolean isReady(String binderName, Binder<?, ?, ?> binder);
}
