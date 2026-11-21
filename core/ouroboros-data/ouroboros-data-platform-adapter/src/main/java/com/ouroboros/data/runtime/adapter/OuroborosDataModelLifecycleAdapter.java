package com.ouroboros.data.runtime.adapter;

import java.util.Objects;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import com.ouroboros.data.runtime.lifecycle.DataModelLifecycleBridge;
import com.ouroboros.data.runtime.lifecycle.DataModelLifecycleBridges;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;

@Configuration
public class OuroborosDataModelLifecycleAdapter {
  private static final Logger logger = LoggerFactory.getLogger(OuroborosDataModelLifecycleAdapter.class);

  private final DataModelLifecycleBridge lifecycleBridge;

  public OuroborosDataModelLifecycleAdapter() {
    this(DataModelLifecycleBridges.primary());
  }

  OuroborosDataModelLifecycleAdapter(DataModelLifecycleBridge lifecycleBridge) {
    this.lifecycleBridge = Objects.requireNonNull(lifecycleBridge);
  }

  @PostConstruct
  void init() {
    logger.info("Ouroboros Data Station Auto Configuration loaded");
  }

  @EventListener
  @Order(10)
  void onOuroborosModelRefreshing(OuroborosModelRefreshingEvent event) {
    lifecycleBridge.refresh();
  }
}
