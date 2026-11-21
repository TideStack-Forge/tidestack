package com.ouroboros.businessmodel.event;

import java.util.Map;
import java.util.TreeMap;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Administrative model-to-service-account assignment; never a subscription permission override. */
@ConfigurationProperties(prefix = "ouroboros.business-event.consumer")
public class BusinessEventConsumerProperties {
  private Map<String, String> principals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  public Map<String, String> getPrincipals() { return principals; }
  public void setPrincipals(Map<String, String> value) {
    principals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    if (value != null) { principals.putAll(value); }
  }
}
