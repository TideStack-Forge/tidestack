package com.ouroboros.businessmodel.event;

import org.springframework.context.event.EventListener;

/** Bridges only explicit platform dispatch signals, never ordinary Spring business payloads. */
public class BusinessEventSpringDispatcher {
  @EventListener
  public void dispatch(BusinessEventSpringAdapter.DispatchSignal signal) {
    signal.dispatch();
  }
}
