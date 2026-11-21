package com.ouroboros.mq.binding;

import java.util.List;

public interface MQBindingIntegrator {
  List<MQBindingProperties<?>> getAllBindings();
}
