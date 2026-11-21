package com.ouroboros.businesslog;

import java.util.List;

/**
 * @author liansz
 **/
public interface BusinessOperationTemplate {

  String getHttpMethod();

  String getUrl();

  String getModuleName();

  String getOperation();

  String getLevel();

  String getBusinessKeyName();

  String getSummaryTemplate();

  List<String> getSensitiveFields();

  Boolean isShouldSaveResponseBody();
}
