package com.ouroboros.businesslog;

import java.util.List;

/**
 * 默认业务日志模板定义。
 *
 * @author liansz
 */
public class DefaultBusinessOperationTemplate implements BusinessOperationTemplate {

  /**
   * 开发态稳定标识。
   */
  private String name;

  /**
   * HTTP 方法。
   */
  private String httpMethod;

  /**
   * URL。
   */
  private String url;

  /**
   * 模块名。
   */
  private String moduleName;

  /**
   * 操作名。
   */
  private String operation;

  /**
   * 日志级别。
   */
  private String level;

  /**
   * 业务键。
   */
  private String businessKeyName;

  /**
   * 摘要模板。
   */
  private String summaryTemplate;

  /**
   * 脱敏字段。
   */
  private List<String> sensitiveFields;

  /**
   * 是否保存响应体。
   */
  private Boolean shouldSaveResponseBody;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String getHttpMethod() {
    return httpMethod;
  }

  public void setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
  }

  @Override
  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public String getModuleName() {
    return moduleName;
  }

  public void setModuleName(String moduleName) {
    this.moduleName = moduleName;
  }

  @Override
  public String getOperation() {
    return operation;
  }

  public void setOperation(String operation) {
    this.operation = operation;
  }

  @Override
  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  @Override
  public String getBusinessKeyName() {
    return businessKeyName;
  }

  public void setBusinessKeyName(String businessKeyName) {
    this.businessKeyName = businessKeyName;
  }

  @Override
  public String getSummaryTemplate() {
    return summaryTemplate;
  }

  public void setSummaryTemplate(String summaryTemplate) {
    this.summaryTemplate = summaryTemplate;
  }

  @Override
  public List<String> getSensitiveFields() {
    return sensitiveFields;
  }

  public void setSensitiveFields(List<String> sensitiveFields) {
    this.sensitiveFields = sensitiveFields;
  }

  @Override
  public Boolean isShouldSaveResponseBody() {
    return shouldSaveResponseBody;
  }

  public void setShouldSaveResponseBody(Boolean shouldSaveResponseBody) {
    this.shouldSaveResponseBody = shouldSaveResponseBody;
  }
}
