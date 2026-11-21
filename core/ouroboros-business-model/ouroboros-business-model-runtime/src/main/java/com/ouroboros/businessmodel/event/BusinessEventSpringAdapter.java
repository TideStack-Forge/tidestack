package com.ouroboros.businessmodel.event;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import com.ouroboros.businessmodel.BusinessBindingCapabilities;
import com.ouroboros.exception.BusinessException;

/** Explicit, single-path Spring transport for an already authorized platform listener binding. */
public final class BusinessEventSpringAdapter implements BusinessEventListener {
  private final BusinessEventListener target;
  private final ApplicationEventPublisher publisher;

  public BusinessEventSpringAdapter(BusinessEventListener target, ApplicationEventPublisher publisher) {
    this.target = Objects.requireNonNull(target);
    this.publisher = Objects.requireNonNull(publisher);
  }

  @Override
  public String getName() {
    return target.getName();
  }

  @Override
  public java.util.Map<String, Object> getListenerConfigSchema() {
    return target.getListenerConfigSchema();
  }

  @Override
  public java.util.Map<String, Object> getListenerInputSchema() { return target.getListenerInputSchema(); }

  @Override
  public java.util.Map<String, Object> getListenerInputSchema(java.util.Map<String, Object> config) { return target.getListenerInputSchema(config); }

  @Override
  public BusinessEventDeliveryMode getDeliveryMode() { return target.getDeliveryMode(); }

  @Override
  public String getPayloadSchemaFingerprint() {
    return target.getPayloadSchemaFingerprint();
  }

  @Override
  public String getImplementationRef() {
    return target.getImplementationRef();
  }

  @Override
  public String getImplementationVersion() {
    return target.getImplementationVersion();
  }

  @Override
  public void onEvent(BusinessEventOccurrence<?> occurrence, BusinessEventDeliveryContext delivery) {
    if (!BusinessBindingCapabilities.isSynchronous(target)) {
      throw new BusinessException("Spring 业务 Event 渠道的目标不能承诺同步完成");
    }
    var signal = new DispatchSignal(delivery.deliveryId(), () -> target.onEvent(occurrence, delivery));
    try {
      publisher.publishEvent(signal);
    } finally {
      signal.accepting = false;
    }
    if (!signal.completed) {
      throw new BusinessException("Spring 业务 Event 渠道尚未完成同步处理");
    }
  }

  /** The Spring broadcast carries no business payload; dispatch only invokes the selected target. */
  public static final class DispatchSignal extends ApplicationEvent {
    private static final long serialVersionUID = 1L;
    private final String deliveryId;
    private final Thread origin = Thread.currentThread();
    private final AtomicBoolean claimed = new AtomicBoolean();
    private final Runnable selectedTarget;
    private volatile boolean completed;
    private volatile boolean accepting = true;

    private DispatchSignal(String deliveryId, Runnable selectedTarget) {
      super("business-event-delivery");
      this.deliveryId = deliveryId;
      this.selectedTarget = selectedTarget;
    }

    public String getDeliveryId() {
      return deliveryId;
    }

    public void dispatch() {
      if (!accepting || Thread.currentThread() != origin) {
        throw new BusinessException("Spring 业务 Event 渠道必须在原交付事务线程完成");
      }
      if (claimed.compareAndSet(false, true)) {
        selectedTarget.run();
        completed = true;
      }
    }
  }
}
