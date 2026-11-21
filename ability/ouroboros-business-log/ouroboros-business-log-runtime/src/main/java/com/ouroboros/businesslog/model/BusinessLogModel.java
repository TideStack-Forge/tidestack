package com.ouroboros.businesslog.model;

import java.time.LocalDateTime;

import com.ouroboros.businesslog.BusinessLog;
import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.util.JsonBag;

/**
 * @author liansz
 **/
@Model(rawName = "t_sys_business_log", label = "业务日志")
public class BusinessLogModel implements BusinessLog {

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  /**
   * 模块名
   */
  @Field
  private String moduleName;

  /**
   * 操作名
   */
  @Field
  private String operation;

  /**
   * 日志级别
   */
  @Field
  private String level;

  /**
   * 操作用户
   */
  @Field(nullable = true)
  private String operator;

  /**
   * 员工
   */
  @Field(nullable = true, type = "Employee")
  private Employee employee;

  /**
   * 操作时间
   */
  @Field
  private LocalDateTime operationTime;

  /**
   * 业务键
   */
  @Field(nullable = true)
  private String businessKey;

  /**
   * 摘要文本
   */
  @Field(nullable = true)
  private String summary;

  /**
   * 操作结果
   *
   * @link com.ouroboros.businesslog.constant.OperationResultEnum
   */
  @Field
  private Integer operationResult;

  /**
   * 数据载荷
   */
  @Field(nullable = true)
  private JsonBag payload;

  /**
   * 响应内容
   */
  @Field(nullable = true)
  private JsonBag responseBody;

  /**
   * 浏览器信息
   */
  @Field(nullable = true)
  private String userAgent;

  /**
   * IP 地址
   */
  @Field(nullable = true)
  private String ipAddress;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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
  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }

  public Employee getEmployee() {
    return employee;
  }

  public void setEmployee(Employee employee) {
    this.employee = employee;
  }

  @Override
  public LocalDateTime getOperationTime() {
    return operationTime;
  }

  public void setOperationTime(LocalDateTime operationTime) {
    this.operationTime = operationTime;
  }

  @Override
  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  @Override
  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  @Override
  public Integer getOperationResult() {
    return operationResult;
  }

  public void setOperationResult(Integer operationResult) {
    this.operationResult = operationResult;
  }

  @Override
  public JsonBag getPayload() {
    return payload;
  }

  public void setPayload(JsonBag payload) {
    this.payload = payload;
  }

  @Override
  public JsonBag getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(JsonBag responseBody) {
    this.responseBody = responseBody;
  }

  @Override
  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  @Override
  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }
}
