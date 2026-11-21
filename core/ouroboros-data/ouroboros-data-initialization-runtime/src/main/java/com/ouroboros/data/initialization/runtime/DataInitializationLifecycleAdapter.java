package com.ouroboros.data.initialization.runtime;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

import com.ouroboros.data.initialization.DataInitializationReconciler;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent;

public class DataInitializationLifecycleAdapter {
  private static final Logger logger = LoggerFactory.getLogger(DataInitializationLifecycleAdapter.class);

  private final DataInitializationReconciler reconciler;

  public DataInitializationLifecycleAdapter(DataInitializationReconciler reconciler) {
    this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
  }

  @EventListener(OuroborosModelRefreshedEvent.class)
  @Order(30)
  public void onModelRefreshed() {
    var result = reconciler.reconcile();
    logger.info("Ouroboros data initialization completed: plans={}, blocks={}, skippedBlocks={}, inserted={}, skipped={}",
        result.plans(), result.blocks(), result.skippedBlocks(), result.inserted(), result.skipped());
  }
}
