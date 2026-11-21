package com.ouroboros.data.runtime.adapter;

import java.util.Objects;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import com.ouroboros.data.typed.runtime.ModelRefreshedListener;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;

@Configuration
public class OuroborosTypedModelLifecycleAdapter {
  private final ModelRefreshedListener modelRefreshedListener;

  public OuroborosTypedModelLifecycleAdapter(ModelRefreshedListener modelRefreshedListener) {
    this.modelRefreshedListener = Objects.requireNonNull(modelRefreshedListener);
  }

  @EventListener
  @Order(20)
  void onOuroborosModelRefreshed(OuroborosModelRefreshedEvent event) {
    modelRefreshedListener.onModelRefreshed();
  }
}
