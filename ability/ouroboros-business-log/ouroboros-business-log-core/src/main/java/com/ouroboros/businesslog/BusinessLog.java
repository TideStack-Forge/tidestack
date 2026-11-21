package com.ouroboros.businesslog;

import java.time.LocalDateTime;

import com.ouroboros.util.JsonBag;

public interface BusinessLog {

  String getModuleName();

  String getOperation();

  String getLevel();

  String getOperator();

  LocalDateTime getOperationTime();

  String getBusinessKey();

  String getSummary();

  Integer getOperationResult();

  JsonBag getPayload();

  JsonBag getResponseBody();

  String getUserAgent();

  String getIpAddress();
}
