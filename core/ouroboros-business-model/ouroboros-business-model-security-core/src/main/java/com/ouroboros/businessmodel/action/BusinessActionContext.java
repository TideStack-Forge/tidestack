package com.ouroboros.businessmodel.action;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.data.record.Record;
import com.ouroboros.security.identity.SecurityIdentity;

/** Trusted execution facts supplied to a high-code Action handler. */
public record BusinessActionContext(String modelFullName,
                                    String actionName,
                                    String authorityName,
                                    String executionId,
                                    String parentExecutionId,
                                    Map<String, Record> objectStates,
                                    Map<String, Object> request,
                                    SecurityIdentity actor,
                                    boolean readOnly) {
  public BusinessActionContext {
    objectStates = Collections.unmodifiableMap(new LinkedHashMap<>(objectStates));
  }
}
