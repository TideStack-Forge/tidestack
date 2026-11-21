package com.ouroboros.mq.binding;

import java.util.Map;

public class MQConsumerProperties extends MQBindingProperties<MQConsumerProperties> {
  private String flowName;

  public String getFlowName() {
    return flowName;
  }

  public MQConsumerProperties setFlowName(String flowName) {
    this.flowName = flowName;
    return this;
  }

  @Override
  protected void addTypedHydratedProps(Map<String, Object> hydrated) {
    putHydratedProp(hydrated, "flowName", flowName);
  }

  @Override
  protected void fillTypedNullsFrom(MQBindingProperties<?> source) {
    if (flowName == null && source instanceof MQConsumerProperties consumerProperties) {
      flowName = consumerProperties.getFlowName();
    }
  }
}
