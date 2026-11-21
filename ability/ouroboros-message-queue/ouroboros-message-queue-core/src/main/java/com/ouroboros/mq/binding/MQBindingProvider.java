package com.ouroboros.mq.binding;

import java.util.List;

public interface MQBindingProvider {
  List<MQBindingProperties<?>> getAllBindings();
}
